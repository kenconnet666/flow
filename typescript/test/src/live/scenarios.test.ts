/**
 * 场景用例（spec/scenarios，同 Java flow-it 的 ScenarioTest）：每个场景清库建表，组装一个固定时钟的引擎，发布表单与流程，
 * 再逐步执行命令、比期望。每一步的结果写成线上的 JSON 形状（编号是文字），所以两个后端比的是同一份期望。
 * 用到 TS 引擎还没有的命令的场景记为跳过，并列出缺的命令。
 */
import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { after, describe, it } from 'node:test';
import { FlowError, type Id } from '@zerodep-dev/flow-model';
import { createFlowEngine, type CommandResult, type FlowEngine } from '@zerodep-dev/flow-core';
import { mysqlDriver } from '@zerodep-dev/query-mysql';
import { livePool, MovableClock, recreateTables, testExtensions, testOrg, type TestUser } from './support.js';

const DIR = join(import.meta.dirname, '..', '..', '..', '..', 'spec', 'scenarios');

/** 发布表单与流程的人 */
const ADMIN: TestUser = { id: 1000n, name: '管理员', roles: new Set(), departments: new Set() };

interface Step {
  readonly id?: string;
  readonly as?: string;
  readonly command: string;
  readonly args?: Record<string, unknown>;
  readonly expect?: Record<string, unknown>;
}

interface Scenario {
  readonly name: string;
  readonly forms?: string[];
  readonly processes?: string[];
  readonly clock: string;
  readonly actors: Record<string, { id: number; name?: string; roles?: number[]; departments?: number[] }>;
  readonly steps: Step[];
}

/** TS 引擎已经有的命令 */
type Run = (engine: FlowEngine<TestUser>, actor: TestUser, args: Args) => Promise<unknown>;

const COMMANDS: Record<string, Run> = {
  start: async (e, a, x) =>
    commandResult(
      await e.start(a, {
        key: x.text('key')!,
        form: x.map('form'),
        variables: x.map('variables'),
        businessKey: x.text('businessKey'),
        title: x.text('title'),
        priority: x.number('priority', 0),
        picks: x.picks(),
      }),
    ),
  claim: async (e, a, x) => commandResult(await e.claim(a, { workItemId: x.id('workItem')! })),
  complete: async (e, a, x) =>
    commandResult(
      await e.complete(a, {
        workItemId: x.id('workItem')!,
        outcome: x.text('outcome'),
        comment: x.text('comment'),
        form: x.map('form'),
        picks: x.picks(),
        returnTarget: x.text('returnTarget'),
      }),
    ),
  withdraw: async (e, a, x) => commandResult(await e.withdraw(a, { instanceId: x.id('instance')!, reason: x.text('reason') })),
  withdrawStep: async (e, a, x) => commandResult(await e.withdrawStep(a, { workItemId: x.id('workItem')!, reason: x.text('reason') })),
  startable: async (e, a) => ({ processes: await e.startable(a) }),
  startView: (e, a, x) => e.startView(a, x.text('key')!),
  todo: (e, a, x) => e.todo(a, x.number('page', 1), x.number('size', 20)),
  done: (e, a, x) => e.done(a, x.number('page', 1), x.number('size', 20)),
  started: (e, a, x) => e.started(a, x.number('page', 1), x.number('size', 20)),
  designations: (e, a, x) => e.designations(a, x.number('page', 1), x.number('size', 20)),
  ccList: (e, a, x) => e.ccList(a, x.number('page', 1), x.number('size', 20)),
  workItem: (e, a, x) => e.workItem(a, x.id('workItem')!),
  instance: (e, a, x) => e.instance(a, x.id('instance')!),
  instances: (e, a, x) =>
    e.instances(a, { status: x.text('status'), processKey: x.text('processKey'), keyword: x.text('keyword'), startedBy: x.id('startedBy') }, x.number('page', 1), x.number('size', 20)),
  processes: async (e, a) => ({ processes: await e.processes(a) }),
  versions: async (e, a, x) => ({ versions: await e.versions(a, x.text('key')!) }),
  validateDefinition: async (e, a, x) => ({ problems: await e.validateDefinition(a, JSON.stringify(x.raw('definition'))) }),
  disableProcess: (e, a, x) => e.setProcessActive(a, x.text('key')!, false),
  enableProcess: (e, a, x) => e.setProcessActive(a, x.text('key')!, true),
  publishDefinition: async (e, a, x) => {
    await e.publishDefinition(a, JSON.stringify(x.raw('definition')));
    return {};
  },
  transfer: async (e, a, x) => commandResult(await e.transfer(a, { workItemId: x.id('workItem')!, userId: x.id('user')!, reason: x.text('reason') })),
  delegate: async (e, a, x) => commandResult(await e.delegate(a, { workItemId: x.id('workItem')!, userId: x.id('user')!, reason: x.text('reason') })),
  addSigners: async (e, a, x) =>
    commandResult(await e.addSigners(a, { workItemId: x.id('workItem')!, users: x.ids('users'), mode: x.text('mode') as 'before', reason: x.text('reason') })),
  removeSigners: async (e, a, x) => commandResult(await e.removeSigners(a, { workItemId: x.id('workItem')!, users: x.ids('users') })),
  release: async (e, a, x) => commandResult(await e.release(a, { workItemId: x.id('workItem')!, reason: x.text('reason') })),
  remind: async (e, a, x) => commandResult(await e.remind(a, { instanceId: x.id('instance')!, workItemId: x.id('workItem'), message: x.text('message') })),
  comment: async (e, a, x) => commandResult(await e.comment(a, { instanceId: x.id('instance')!, text: x.text('text')! })),
  signal: async (e, a, x) => ({
    results: (
      await e.signal(a, { instanceId: x.id('instance'), businessKey: x.text('businessKey'), event: x.text('event')!, outcome: x.text('outcome'), data: x.map('data') })
    ).map(commandResult),
  }),
  assign: async (e, a, x) => commandResult(await e.assign(a, { workItemId: x.id('workItem')!, userId: x.id('user')! })),
  retryJob: async (e, a, x) => ({ revived: await e.retryJob(a, { jobId: x.id('job')! }) }),
  skipJob: async (e, a, x) => commandResult(await e.skipJob(a, { jobId: x.id('job')!, outcome: x.text('outcome'), reason: x.text('reason') })),
  cc: async (e, a, x) => commandResult(await e.cc(a, { instanceId: x.id('instance')!, users: x.ids('users'), message: x.text('message') })),
  readCc: async (e, a, x) => ({ read: await e.readCc(a, { instanceId: x.id('instance')! }) }),
  terminate: async (e, a, x) => commandResult(await e.terminate(a, { instanceId: x.id('instance')!, reason: x.text('reason') })),
  suspend: async (e, a, x) => commandResult(await e.suspend(a, { instanceId: x.id('instance')!, reason: x.text('reason') })),
  resume: async (e, a, x) => commandResult(await e.resume(a, { instanceId: x.id('instance')! })),
  jump: async (e, a, x) => commandResult(await e.jump(a, { instanceId: x.id('instance')!, node: x.text('node')!, reason: x.text('reason') })),
  reassign: async (e, a, x) => commandResult(await e.reassign(a, { workItemId: x.id('workItem')!, userId: x.id('user')!, reason: x.text('reason') })),
  incidents: (e, a, x) => e.incidents(a, x.id('instance'), x.number('page', 1), x.number('size', 20)),
  forms: async (e, a) => ({ forms: await e.forms(a) }),
  formVersions: async (e, a, x) => ({ versions: await e.formVersions(a, x.text('key')!) }),
  formDocument: (e, a, x) => e.formDocument(a, x.text('key')!, x.raw('version') == null ? null : x.number('version', 0)),
  disableForm: (e, a, x) => e.setFormActive(a, x.text('key')!, false),
  enableForm: (e, a, x) => e.setFormActive(a, x.text('key')!, true),
  publishForm: async (e, a, x) => {
    const form = await e.publishForm(a, {
      key: x.text('key')!,
      name: x.text('name')!,
      category: x.text('category'),
      manifest: JSON.stringify(x.raw('manifest')),
      clientSource: x.text('clientSource'),
      note: x.text('note'),
    });
    return { id: form.id, key: form.formKey, version: form.version }; // 同 HTTP 接口的响应
  },
};

/** 命令的结果写成场景里的形状 */
const commandResult = (r: CommandResult) => ({ instance: r.instance, workItems: r.created, closed: r.closed, events: r.events });

/** 一步的参数 */
class Args {
  constructor(private readonly values: Record<string, unknown>) {}

  text(name: string): string | null {
    return (this.values[name] as string | undefined) ?? null;
  }

  /** 编号：引用解出来的是线上的文字，直接写的是数字（用户编号） */
  id(name: string): Id | null {
    const value = this.values[name];
    return value == null ? null : BigInt(value as string | number);
  }

  number(name: string, fallback: number): number {
    const value = this.values[name];
    return value == null ? fallback : Number(value);
  }

  map(name: string): Record<string, unknown> {
    return (this.values[name] as Record<string, unknown> | undefined) ?? {};
  }

  raw(name: string): unknown {
    return this.values[name];
  }

  /** 一串编号 */
  ids(name: string): Id[] {
    return ((this.values[name] as (string | number)[] | undefined) ?? []).map((v) => BigInt(v));
  }

  /** 下一步选的处理人：{ 节点: [用户编号…] } */
  picks(): Record<string, Id[]> {
    return Object.fromEntries(Object.entries(this.map('picks')).map(([node, users]) => [node, (users as (string | number)[]).map((u) => BigInt(u))]));
  }
}

/** 结果写成线上的 JSON 形状：编号（bigint）写成文字，去掉 undefined */
function wire(value: unknown): unknown {
  if (typeof value === 'bigint') return String(value);
  if (Array.isArray(value)) return value.map(wire);
  if (value instanceof Set) return [...value].map(wire);
  if (typeof value === 'object' && value !== null) {
    const out: Record<string, unknown> = {};
    for (const [key, item] of Object.entries(value)) if (item !== undefined) out[key] = wire(item);
    return out;
  }
  return value;
}

/** 比期望：只比写出来的字段；列表一样长、逐项比；null 表示为空；期望是文字、实际是事件时按事件种类比；数字按数值比 */
function check(path: string, expected: unknown, actual: unknown): void {
  if (expected === null) {
    if (actual != null) assert.fail(`${path}: expected nothing but was ${JSON.stringify(actual)}`);
  } else if (Array.isArray(expected)) {
    if (!Array.isArray(actual) || actual.length !== expected.length) {
      assert.fail(`${path}: expected ${expected.length} item(s) ${JSON.stringify(expected)} but was ${JSON.stringify(actual)}`);
    }
    expected.forEach((item, i) => {
      const a = (actual as unknown[])[i];
      const asEvent = typeof item === 'string' && typeof a === 'object' && a !== null && 'type' in a ? (a as { type: unknown }).type : a;
      check(`${path}[${i}]`, item, asEvent);
    });
  } else if (typeof expected === 'object') {
    if (typeof actual !== 'object' || actual === null) assert.fail(`${path}: expected an object but was ${JSON.stringify(actual)}`);
    for (const [key, value] of Object.entries(expected as Record<string, unknown>)) check(`${path}.${key}`, value, (actual as Record<string, unknown>)[key]);
  } else if (typeof expected === 'number' && (typeof actual === 'number' || typeof actual === 'string')) {
    if (Number(expected) !== Number(actual)) assert.fail(`${path}: expected ${expected} but was ${JSON.stringify(actual)}`);
  } else if (expected !== actual) {
    assert.fail(`${path}: expected ${JSON.stringify(expected)} but was ${JSON.stringify(actual)}`);
  }
}

/** 参数里的引用（`$名字.路径`、`$.路径`）换成前面步骤的结果里的编号 */
function resolve(args: Record<string, unknown>, named: Map<string, unknown>, previous: unknown): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(args)) out[key] = typeof value === 'string' && value.startsWith('$') ? reference(value, named, previous) : value;
  return out;
}

function reference(text: string, named: Map<string, unknown>, previous: unknown): unknown {
  const m = /^\$([\w-]*)\.(.+)$/.exec(text);
  if (!m) throw new Error(`bad reference ${text}`);
  let value = m[1] === '' ? previous : named.get(m[1]!);
  if (value === undefined) throw new Error(`reference ${text} names no earlier step`);
  for (const segment of m[2]!.split('.')) {
    const s = /^(\w+)(?:\[(\d+)])?$/.exec(segment);
    if (!s) throw new Error(`bad reference ${text}`);
    value = (value as Record<string, unknown>)[s[1]!];
    if (s[2] !== undefined) value = (value as unknown[])[Number(s[2])];
  }
  // 引用到一个对象（待办、实例）时取它的编号
  if (typeof value === 'object' && value !== null) return (value as { id: string }).id;
  return typeof value === 'number' ? String(value) : value;
}

const pool = livePool();
after(() => pool?.end());

describe('场景用例（spec/scenarios）', { skip: pool === null ? 'FLOW_MYSQL_URL not set' : false }, () => {
  for (const file of readdirSync(DIR).filter((f) => f.endsWith('.json')).sort()) {
    const scenario = JSON.parse(readFileSync(join(DIR, file), 'utf8')) as Scenario;
    const missing = [...new Set(scenario.steps.map((s) => s.command))].filter((c) => c !== 'runJobs' && !(c in COMMANDS));
    const skip = missing.length > 0 ? `TS 引擎还没有：${missing.join('、')}` : false;
    it(`${file}：${scenario.name}`, { skip }, async () => run(scenario));
  }
});

async function run(scenario: Scenario) {
  await recreateTables(pool!);
  const actors = new Map<string, TestUser>();
  for (const [alias, a] of Object.entries(scenario.actors)) {
    actors.set(alias, {
      id: BigInt(a.id),
      name: a.name ?? alias,
      roles: new Set((a.roles ?? []).map(BigInt)),
      departments: new Set((a.departments ?? []).map(BigInt)),
    });
  }
  const clock = new MovableClock(scenario.clock);
  const engine = createFlowEngine<TestUser>({ driver: mysqlDriver(pool!), org: testOrg([...actors.values()]), clock, ...testExtensions() });
  for (const path of scenario.forms ?? []) {
    const form = JSON.parse(readFileSync(join(DIR, path), 'utf8')) as { key: string; name: string; manifest: unknown };
    await engine.publishForm(ADMIN, { key: form.key, name: form.name, manifest: JSON.stringify(form.manifest) });
  }
  for (const path of scenario.processes ?? []) {
    try {
      await engine.publishDefinition(ADMIN, readFileSync(join(DIR, path), 'utf8'));
    } catch (error) {
      assert.fail(`publishing ${path} failed: ${JSON.stringify(error instanceof FlowError ? error.errors : String(error))}`);
    }
  }
  const named = new Map<string, unknown>();
  let previous: unknown = {};
  for (const [i, step] of scenario.steps.entries()) {
    const where = `step ${i + 1} (${step.as} ${step.command})`;
    // 拨时钟、做到期的作业：引擎自己做的事，没有操作人
    if (step.command === 'runJobs') {
      clock.moveTo((step.args ?? {})['at'] as string);
      const result = { ran: await engine.runDueJobs() };
      check(where, step.expect ?? {}, result);
      if (step.id) named.set(step.id, result);
      previous = result;
      continue;
    }
    const actor = actors.get(step.as ?? '');
    if (!actor) throw new Error(`${where}: unknown actor ${step.as}`);
    const args = new Args(resolve(step.args ?? {}, named, previous));
    let result: unknown;
    try {
      result = wire(await COMMANDS[step.command]!(engine, actor, args));
    } catch (error) {
      if (!(error instanceof FlowError)) throw new Error(`${where}: ${error instanceof Error ? error.stack : String(error)}`);
      result = { error: wire({ code: error.code, params: error.params, errors: error.errors }) };
    }
    const expect = step.expect ?? {};
    const failed = typeof result === 'object' && result !== null && 'error' in result;
    if (failed && !('error' in expect)) assert.fail(`${where}: unexpected ${JSON.stringify((result as { error: unknown }).error)}`);
    if (!failed && 'error' in expect) assert.fail(`${where}: expected ${JSON.stringify(expect['error'])} but it succeeded`);
    check(where, expect, result);
    if (step.id) named.set(step.id, result);
    previous = result;
  }
}
