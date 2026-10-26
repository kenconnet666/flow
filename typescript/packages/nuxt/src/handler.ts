/**
 * 引擎自带的 HTTP 接口（0006 的 7.3），h3 的处理函数。路径、参数、返回值与 Java flow-spring 的 FlowHttpController 相同，
 * 前端（flow-ui 的 createFlowClient）接哪个后端都一样：
 *
 *   POST {前缀}/commands/start、claim、complete、withdraw、transfer、delegate、add-signers、remove-signers、release、remind、
 *        comment、cc、read-cc、signal、assign、withdraw-step
 *   POST {前缀}/admin/terminate、suspend、resume、jump、reassign、retry-job、skip-job
 *   POST {前缀}/definitions/validate、publish（?note=）、disable、enable；forms/publish、disable、enable
 *   GET  {前缀}/queries/startable、start/{key}、todo、done、started、cc、designations（?page=&pageSize=）、
 *        work-items/{id}、instances/{id}、instances（?status=&processKey=&keyword=&startedBy=）、processes、
 *        processes/{key}/versions、processes/{key}/definition（?version=）、incidents（?instanceId=）、
 *        forms、forms/{key}/versions、forms/{key}/document（?version=）
 *
 * Nuxt 里放在 `server/routes/flow/[...path].ts`：
 *
 *   export default createFlowHandler({ engine, actor: (event) => userOf(event.context.session) });
 *
 * 命令（POST）的请求头带 `Idempotency-Key` 时幂等：同一个键再来时返回第一次的响应体，不再做一遍。
 * HTTP 一律 200，成败看响应体的 `code`（0008）；认不出的错误是 `flow.internal-error`，并交给 onError 记下。
 * 登录与接口的保护由应用负责：actor 取不到人时抛 FlowError（如 `flow.forbidden`，同样是 200 加错误码），
 * 或抛 h3 的错误（`createError({ statusCode: 401 })`，原样交给 h3）。
 */
import { apiFail, apiOk } from '@zerodep-dev/flow-model';
import type { Actor, FlowEngine } from '@zerodep-dev/flow-core';
import {
  createRouter,
  defineEventHandler,
  getHeader,
  getQuery,
  getRouterParam,
  isError,
  readRawBody,
  setResponseHeader,
  useBase,
  type EventHandler,
  type H3Event,
} from 'h3';
import { idOf, intOf, RequestBody } from './request-body.js';
import { commandShape, writeResult } from './response-body.js';

export interface FlowHandlerOptions<U extends Actor> {
  readonly engine: FlowEngine<U>;
  /** 当前操作人：从会话取（同 Java 的 ActorProvider） */
  readonly actor: (event: H3Event) => U | Promise<U>;
  /** 路径前缀，默认 `/flow`（同 Spring 的 flow.http.path） */
  readonly prefix?: string;
  /** 认不出的错误（`flow.internal-error`）记到哪；默认 console.error */
  readonly onError?: (error: unknown, event: H3Event) => void;
}

/** 一个请求里路由要用的东西 */
class Call {
  constructor(readonly event: H3Event) {}

  async body(): Promise<RequestBody> {
    return RequestBody.parse(await this.raw());
  }

  async raw(): Promise<string> {
    return (await readRawBody(this.event, 'utf8')) ?? '';
  }

  param(name: string): string {
    return getRouterParam(this.event, name, { decode: true }) ?? '';
  }

  /** 查询参数；写了几次时取第一个，空的当没写 */
  query(name: string): string | null {
    const value = getQuery(this.event)[name];
    const first = Array.isArray(value) ? value[0] : value;
    return first == null || first === '' ? null : String(first);
  }

  page(): number {
    return intOf('page', this.query('page'), 1);
  }

  pageSize(): number {
    return intOf('pageSize', this.query('pageSize'), 20);
  }

  version(): number | null {
    const version = this.query('version');
    return version == null ? null : intOf('version', version, 0);
  }
}

interface Route<U extends Actor> {
  readonly method: 'get' | 'post';
  readonly path: string;
  /** 命令的名字（幂等记录里用）；查询与校验没有，不走幂等 */
  readonly command?: string;
  readonly run: (actor: U, call: Call) => Promise<unknown>;
}

/** 全部路由；顺序同 Java 的控制器，便于对照 */
function flowRoutes<U extends Actor>(engine: FlowEngine<U>): Route<U>[] {
  const command = (path: string, run: (actor: U, body: RequestBody, call: Call) => Promise<unknown>): Route<U> => ({
    method: 'post',
    path,
    command: path,
    run: async (actor, call) => run(actor, await call.body(), call),
  });
  const query = (path: string, run: Route<U>['run']): Route<U> => ({ method: 'get', path, run });

  return [
    command('commands/start', (a, b) => engine.start(a, b.start()).then(commandShape)),
    command('commands/claim', (a, b) => engine.claim(a, b.claim()).then(commandShape)),
    command('commands/complete', (a, b) => engine.complete(a, b.complete()).then(commandShape)),
    command('commands/withdraw', (a, b) => engine.withdraw(a, b.withdraw()).then(commandShape)),

    // 协作与管理员命令（0006 的 3.1–3.4）；管理员命令谁能做由 PermissionPolicy 定
    command('commands/transfer', (a, b) => engine.transfer(a, b.handOver()).then(commandShape)),
    command('commands/delegate', (a, b) => engine.delegate(a, b.handOver()).then(commandShape)),
    command('commands/add-signers', (a, b) => engine.addSigners(a, b.addSigners()).then(commandShape)),
    command('commands/remove-signers', (a, b) => engine.removeSigners(a, b.removeSigners()).then(commandShape)),
    command('commands/release', (a, b) => engine.release(a, b.release()).then(commandShape)),
    command('commands/remind', (a, b) => engine.remind(a, b.remind()).then(commandShape)),
    command('commands/comment', (a, b) => engine.comment(a, b.comment()).then(commandShape)),
    command('admin/terminate', (a, b) => engine.terminate(a, b.instance()).then(commandShape)),
    command('admin/suspend', (a, b) => engine.suspend(a, b.instance()).then(commandShape)),
    command('admin/resume', (a, b) => engine.resume(a, b.instance()).then(commandShape)),
    command('admin/jump', (a, b) => engine.jump(a, b.jump()).then(commandShape)),
    command('admin/reassign', (a, b) => engine.reassign(a, b.handOver()).then(commandShape)),

    // 发布流程定义：请求体就是 0005 的定义 JSON，发布说明放在参数 note 里
    {
      method: 'post',
      path: 'definitions/publish',
      command: 'definitions/publish',
      run: async (a, call) => published(await engine.publishDefinition(a, await call.raw(), call.query('note'))),
    },
    command('forms/disable', (a, b) => engine.setFormActive(a, b.key(), false)),
    command('forms/enable', (a, b) => engine.setFormActive(a, b.key(), true)),
    query('queries/forms', (a) => engine.forms(a)),
    query('queries/forms/:key/versions', (a, c) => engine.formVersions(a, c.param('key'))),
    query('queries/forms/:key/document', (a, c) => engine.formDocument(a, c.param('key'), c.version())),
    command('forms/publish', async (a, b) => {
      const form = await engine.publishForm(a, b.formPublication());
      return { id: form.id, key: form.formKey, version: form.version };
    }),

    query('queries/startable', (a) => engine.startable(a)),
    query('queries/start/:key', (a, c) => engine.startView(a, c.param('key'))),
    // 参数名同 vue-tsx-pro 的表格请求（page、pageSize）
    query('queries/todo', (a, c) => engine.todo(a, c.page(), c.pageSize())),
    query('queries/done', (a, c) => engine.done(a, c.page(), c.pageSize())),
    // 外部事件：返回 { results: [每个往下走的实例这一步的结果] }
    command('commands/signal', async (a, b) => ({ results: (await engine.signal(a, b.signal())).map(commandShape) })),
    query('queries/instances', (a, c) => {
      const startedBy = c.query('startedBy');
      const search = {
        status: c.query('status'),
        processKey: c.query('processKey'),
        keyword: c.query('keyword'),
        startedBy: startedBy == null ? null : idOf('startedBy', startedBy),
      };
      return engine.instances(a, search, c.page(), c.pageSize());
    }),
    // 校验定义、不发布：不是命令，不走幂等
    { method: 'post', path: 'definitions/validate', run: async (a, call) => ({ problems: await engine.validateDefinition(a, await call.raw()) }) },
    command('definitions/disable', (a, b) => engine.setProcessActive(a, b.key(), false)),
    command('definitions/enable', (a, b) => engine.setProcessActive(a, b.key(), true)),
    query('queries/processes/:key/versions', (a, c) => engine.versions(a, c.param('key'))),
    // 某个版本的定义 JSON（发布时的原文）
    query('queries/processes/:key/definition', async (a, c) => ({ json: await engine.definitionJson(a, c.param('key'), c.version()) })),
    query('queries/processes', (a) => engine.processes(a)),
    query('queries/incidents', (a, c) => {
      const instanceId = c.query('instanceId');
      return engine.incidents(a, instanceId == null ? null : idOf('instanceId', instanceId), c.page(), c.pageSize());
    }),
    command('commands/withdraw-step', (a, b) => engine.withdrawStep(a, b.withdrawStep()).then(commandShape)),
    command('commands/assign', (a, b) => engine.assign(a, b.assign()).then(commandShape)),
    query('queries/designations', (a, c) => engine.designations(a, c.page(), c.pageSize())),
    query('queries/cc', (a, c) => engine.ccList(a, c.page(), c.pageSize())),
    command('commands/cc', (a, b) => engine.cc(a, b.cc()).then(commandShape)),
    command('commands/read-cc', async (a, b) => ({ read: await engine.readCc(a, b.readCc()) })),
    command('admin/retry-job', async (a, b) => ({ revived: await engine.retryJob(a, b.retryJob()) })),
    command('admin/skip-job', (a, b) => engine.skipJob(a, b.skipJob()).then(commandShape)),
    query('queries/started', (a, c) => engine.started(a, c.page(), c.pageSize())),
    query('queries/work-items/:id', (a, c) => engine.workItem(a, idOf('id', c.param('id')))),
    query('queries/instances/:id', (a, c) => engine.instance(a, idOf('id', c.param('id')))),
  ];
}

/** 发布的流程版本：`{ id, key, version }`（定义本身就是请求体，不再发回） */
const published = (definition: { id: bigint; processKey: string; version: number }) => ({
  id: definition.id,
  key: definition.processKey,
  version: definition.version,
});

/** 引擎的 HTTP 接口：挂在 prefix 下（默认 `/flow`），别的路径交给后面的处理函数 */
export function createFlowHandler<U extends Actor>(options: FlowHandlerOptions<U>): EventHandler {
  const onError = options.onError ?? ((error: unknown) => console.error('flow request failed', error));
  const router = createRouter();
  for (const route of flowRoutes(options.engine)) {
    router[route.method](
      `/${route.path}`,
      defineEventHandler(async (event) => {
        setResponseHeader(event, 'content-type', 'application/json; charset=utf-8');
        return respond(options, route, new Call(event), onError);
      }),
    );
  }
  return useBase(options.prefix ?? '/flow', router.handler);
}

/**
 * 执行并写成响应体：成功是 `ok` 加数据，出错按业务错误码写。命令的请求头带 `Idempotency-Key` 时幂等地做
 * （同一个键再来时返回第一次的响应体，见 `engine.idempotent`）。
 */
async function respond<U extends Actor>(
  options: FlowHandlerOptions<U>,
  route: Route<U>,
  call: Call,
  onError: (error: unknown, event: H3Event) => void,
): Promise<string> {
  try {
    const actor = await options.actor(call.event);
    const run = async () => writeResult(apiOk(await route.run(actor, call)));
    const key = route.command ? getHeader(call.event, 'idempotency-key')?.trim() : undefined;
    return key ? await options.engine.idempotent(actor, key, route.command!, run) : await run();
  } catch (error) {
    if (isError(error)) throw error; // 应用自己的 HTTP 错误（401……）交给 h3
    const result = apiFail(error);
    if (result.code === 'flow.internal-error') onError(error, call.event);
    return writeResult(result);
  }
}
