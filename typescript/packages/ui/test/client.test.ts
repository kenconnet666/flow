// 客户端：路径与请求体同 0006 的 7.3；响应体按 0008 拆包、typed JSON 还原（编号是 bigint）；出错时是带业务错误码的 HttpError
import { createHttpClient, HttpError } from '@zerodep-dev/vue-tsx-pro-ui';
import { describe, expect, it } from 'vitest';
import { createFlowClient } from '../src/client/flow-client';

/** 记下请求、回固定响应体的 fetch */
function fakeFetch(body: unknown) {
  const requests: { url: string; method: string; body: string | null }[] = [];
  const fetch = async (request: Request) => {
    requests.push({ url: request.url, method: request.method, body: request.method === 'GET' ? null : await request.text() });
    return new Response(JSON.stringify(body), { headers: { 'content-type': 'application/json' } });
  };
  return { requests, fetch: fetch as typeof globalThis.fetch };
}

describe('createFlowClient', () => {
  it('查询拆包并还原编号', async () => {
    const { requests, fetch } = fakeFetch({
      code: 'ok',
      data: { workItem: { id: '7', node: 'review' }, claimable: true, $types: { 'workItem.id': 'bigint' } },
    });
    const flow = createFlowClient(createHttpClient({ baseURL: 'http://test', fetch }));
    const view = await flow.workItem(7n);
    expect(requests[0]).toMatchObject({ url: 'http://test/flow/queries/work-items/7', method: 'GET' });
    expect(view.workItem.id).toBe(7n);
    expect(view.claimable).toBe(true);
  });

  it('命令按 JSON 发，编号写成文字', async () => {
    const { requests, fetch } = fakeFetch({ code: 'ok', data: {} });
    const flow = createFlowClient(createHttpClient({ baseURL: 'http://test', fetch }), { prefix: '/api/flow' });
    await flow.complete({ workItemId: 9007199254740993n, outcome: 'pass', form: { note: '好' } });
    expect(requests[0]?.url).toBe('http://test/api/flow/commands/complete');
    expect(JSON.parse(requests[0]!.body!)).toMatchObject({ workItemId: '9007199254740993', outcome: 'pass', form: { note: '好' } });
  });

  it('我已办、我发起的带分页参数', async () => {
    const { requests, fetch } = fakeFetch({ code: 'ok', data: { rows: [], total: 0, page: 2, size: 10, totalPages: 0 } });
    const flow = createFlowClient(createHttpClient({ baseURL: 'http://test', fetch }));
    await flow.done({ page: 2, pageSize: 10 });
    await flow.started({ page: 1, pageSize: 20 });
    expect(requests.map((r) => r.url)).toEqual([
      'http://test/flow/queries/done?page=2&pageSize=10',
      'http://test/flow/queries/started?page=1&pageSize=20',
    ]);
  });

  it('协作与管理员命令的路径', async () => {
    const { requests, fetch } = fakeFetch({ code: 'ok', data: {} });
    const flow = createFlowClient(createHttpClient({ baseURL: 'http://test', fetch }));
    await flow.addSigners({ workItemId: 3n, users: [1003n, 1005n], mode: 'before' });
    await flow.resume(8n);
    expect(requests[0]?.url).toBe('http://test/flow/commands/add-signers');
    expect(JSON.parse(requests[0]!.body!)).toMatchObject({ workItemId: '3', users: ['1003', '1005'], mode: 'before' });
    expect(requests[1]?.url).toBe('http://test/flow/admin/resume');
    expect(JSON.parse(requests[1]!.body!)).toMatchObject({ instanceId: '8' });
  });

  it('业务错误码抛成 HttpError，带按字段的明细', async () => {
    const { fetch } = fakeFetch({ code: 'flow.validation-failed', errors: [{ code: 'required', field: 'comment' }] });
    const flow = createFlowClient(createHttpClient({ baseURL: 'http://test', fetch }));
    const error = await flow.claim(1n).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(HttpError);
    expect((error as HttpError).code).toBe('flow.validation-failed');
    expect((error as HttpError).errors).toEqual([{ code: 'required', field: 'comment' }]);
  });
});
