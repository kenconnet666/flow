/**
 * flow-nuxt 的 HTTP 接口（0006 的 7.3），同 Java 的 SpringHttpTest：h3 应用挂上 createFlowHandler，当前操作人取请求头 X-User。
 * 走一单采购：发起、财务处理、总经理审批；HTTP 一律 200，成败看响应体的 code（0008）。
 */
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createServer, type Server } from 'node:http';
import type { AddressInfo } from 'node:net';
import { join } from 'node:path';
import { after, before, beforeEach, describe, it } from 'node:test';
import { createFlowEngine } from '@zerodep-dev/flow-core';
import { FlowError } from '@zerodep-dev/flow-model';
import { createFlowHandler } from '@zerodep-dev/flow-nuxt';
import { mysqlDriver } from '@zerodep-dev/query-mysql';
import { createApp, createError, getHeader, toNodeListener } from 'h3';
import { livePool, recreateTables, testExtensions, testOrg, type TestUser } from './support.js';

const DIR = join(import.meta.dirname, '..', '..', '..', '..', 'spec', 'scenarios');

const USERS: readonly TestUser[] = [
  { id: 1001n, name: '林小满', roles: new Set(), departments: new Set() },
  { id: 1002n, name: '周启航', roles: new Set([1n]), departments: new Set() },
  { id: 1004n, name: '总经理', roles: new Set(), departments: new Set() },
];

type Body = Record<string, unknown> & { code: string; data?: Record<string, unknown> };

const pool = livePool();

describe('Nuxt：HTTP 接口', { skip: pool ? false : 'FLOW_MYSQL_URL not set' }, () => {
  const engine = createFlowEngine<TestUser>({ driver: mysqlDriver(pool!), org: testOrg(USERS), ...testExtensions() });
  const app = createApp().use(
    createFlowHandler({
      engine,
      // 没带 X-User 是应用自己的 401；带了但查不到人是引擎的业务错误码（同 Spring 示例）
      actor: (event) => {
        const header = getHeader(event, 'x-user');
        if (header == null) throw createError({ statusCode: 401 });
        const user = USERS.find((u) => String(u.id) === header);
        if (!user) throw new FlowError('flow.forbidden', { params: { reason: 'not-signed-in' } });
        return user;
      },
    }),
  );
  let server: Server;
  let base: string;

  before(async () => {
    server = createServer(toNodeListener(app));
    await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
    base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
  });
  after(async () => {
    await new Promise((resolve) => server.close(resolve));
    await pool?.end();
  });

  /** 发一个请求：断言 HTTP 是 200，返回响应体 */
  async function call(path: string, user: string, body?: string, headers: Record<string, string> = {}): Promise<Body> {
    const response = await fetch(base + path, {
      method: body === undefined ? 'GET' : 'POST',
      headers: { 'x-user': user, 'content-type': 'application/json', ...headers },
      ...(body === undefined ? {} : { body }),
    });
    assert.equal(response.status, 200);
    return (await response.json()) as Body;
  }
  const first = (list: unknown) => (list as Record<string, unknown>[])[0]!;

  beforeEach(async () => {
    await recreateTables(pool!);
    // 经接口发布：表单文件与定义文件原样作为请求体
    const form = await call('/flow/forms/publish', '1001', readFileSync(join(DIR, 'forms/purchase-form.json'), 'utf8'));
    // 编号同别处一样是 typed JSON：写成文字，$types 里记着 bigint
    assert.deepEqual(form.data, { id: form.data!['id'], key: 'purchase-form', version: 1, $types: { id: 'bigint' } });
    const process = await call('/flow/definitions/publish?note=第一版', '1001', readFileSync(join(DIR, 'processes/purchase.json'), 'utf8'));
    assert.equal(process.data!['version'], 1);
    assert.equal((await call('/flow/definitions/publish', '1001', '{"key":"x"}')).code, 'flow.definition-invalid');
  });

  it('一单采购走完，出错也是 200 加业务错误码', async () => {
    const started = await call('/flow/commands/start', '1001', '{"key":"purchase","form":{"title":"办公桌","amount":"12000.00"}}');
    assert.equal(started.code, 'ok');
    const review = first(started.data!['workItems'])['id'] as string;
    const instance = (started.data!['instance'] as Record<string, unknown>)['id'] as string;

    const todo = await call('/flow/queries/todo?page=1&pageSize=10', '1002');
    assert.equal(todo.data!['total'], 1);

    const reviewed = await call('/flow/commands/complete', '1002', JSON.stringify({ workItemId: review, outcome: 'pass', form: { note: '预算内' } }));
    const approve = first(reviewed.data!['workItems'])['id'] as string;

    const again = await call('/flow/commands/complete', '1002', JSON.stringify({ workItemId: review, outcome: 'pass' }));
    assert.equal(again.code, 'flow.work-item-not-open');

    const malformed = await call('/flow/commands/complete', '1002', '{not json');
    assert.equal(malformed.code, 'flow.validation-failed');
    assert.deepEqual(malformed['errors'], [{ code: 'malformed-json', field: 'body' }]);

    const approved = await call('/flow/commands/complete', '1004', JSON.stringify({ workItemId: approve, outcome: 'pass' }));
    assert.equal((approved.data!['instance'] as Record<string, unknown>)['status'], 'completed');

    const detail = await call(`/flow/queries/instances/${instance}`, '1001');
    const form = detail.data!['form'] as { values: Record<string, unknown> };
    assert.equal(form.values['note'], '预算内');
  });

  it('带同一个 Idempotency-Key 的命令只做一次，再来返回第一次的响应', async () => {
    const started = await call('/flow/commands/start', '1001', '{"key":"purchase","form":{"title":"椅子","amount":"100.00"}}');
    const review = first(started.data!['workItems'])['id'] as string;
    const body = JSON.stringify({ workItemId: review, outcome: 'pass' });

    const once = await call('/flow/commands/complete', '1002', body, { 'Idempotency-Key': 'k-1' });
    assert.equal(once.code, 'ok');
    // 不带键再做一次就真的再做：金额小，复核完实例已经结束了
    assert.equal((await call('/flow/commands/complete', '1002', body)).code, 'flow.instance-not-active');
    // 带同一个键：返回第一次的响应，不再做
    assert.deepEqual(await call('/flow/commands/complete', '1002', body, { 'Idempotency-Key': 'k-1' }), once);
    // 同一个键用在别的命令上
    assert.equal((await call('/flow/commands/claim', '1002', body, { 'Idempotency-Key': 'k-1' })).code, 'flow.validation-failed');

    // 定期清理：还在保留期里的不删；过了保留期的删掉，同一个键再来就当新请求真的去做（实例已结束，报错）
    assert.equal(await engine.purgeRequests(24 * 60 * 60), 0);
    assert.equal(await engine.purgeRequests(-1), 1); // 截止拨到一秒之后：刚记下的也算过期
    assert.equal((await call('/flow/commands/complete', '1002', body, { 'Idempotency-Key': 'k-1' })).code, 'flow.instance-not-active');
  });

  it('外部事件返回 { results }；取不到操作人时按应用抛的错误回应', async () => {
    const signal = await call('/flow/commands/signal', '1001', '{"event":"nothing-waits","businessKey":"none"}');
    assert.deepEqual(signal, { code: 'ok', data: { results: [] } });
    assert.deepEqual(await call('/flow/queries/todo', '9999'), {
      code: 'flow.forbidden',
      message: 'flow.forbidden',
      params: { reason: 'not-signed-in' },
    });
    assert.equal((await fetch(`${base}/flow/queries/todo`)).status, 401);
  });
});
