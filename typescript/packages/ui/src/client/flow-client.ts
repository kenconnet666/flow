/**
 * 引擎 HTTP 接口的客户端（0006 的 7.3）：一个命令一个方法，结果的类型在 types.ts。建在组件库的请求层上：
 * 响应体按 0008 拆包（出错时抛带业务错误码的 HttpError），typed JSON 还原（编号是 bigint）。
 *
 *   const flow = createFlowClient(createHttpClient({ headers: { … } }));
 *   provideFlowClient(flow);                     // 应用根上给一次，流程组件里 useFlowClient() 取
 *   const view = await flow.workItem(id);
 *
 * 不给时组件取 `createFlowClient(useHttp())`：用组件库配置里的请求客户端，前缀 /flow。
 */
import type { CcItem, Id, ProcessInstance, TodoItem } from '@zerodep-dev/flow-model';
import { useHttp, type HttpClient, type TablePage } from '@zerodep-dev/vue-tsx-pro-ui';
import { inject, provide, type InjectionKey } from 'vue';
import type {
  AddSignersCommand,
  CcCommand,
  CommandResult,
  FailedJob,
  InstanceSearch,
  ProcessSummary,
  SignalCommand,
  CommentCommand,
  CompleteCommand,
  HandOverCommand,
  InstanceCommand,
  InstanceDetail,
  JumpCommand,
  ReleaseCommand,
  RemindCommand,
  RemoveSignersCommand,
  StartableProcess,
  StartCommand,
  StartView,
  WithdrawCommand,
  WorkItemView,
} from './types';

/** 一页：同 query 的表格接口 */
export interface FlowPage<T> extends TablePage<T> {
  readonly page: number;
  readonly size: number;
}

/** 一页待办 */
export type TodoPage = FlowPage<TodoItem>;

/** 分页的参数 */
export interface PageQuery {
  readonly page: number;
  readonly pageSize: number;
}

/** 引擎接口的全部方法 */
export interface FlowClient {
  /** 我能发起的流程 */
  startable(signal?: AbortSignal): Promise<readonly StartableProcess[]>;
  /** 发起页：流程与开始节点上的表单 */
  startView(key: string, signal?: AbortSignal): Promise<StartView>;
  /** 我的待办 */
  todo(query: PageQuery, signal?: AbortSignal): Promise<TodoPage>;
  /** 我已办：按处理时间从新到旧 */
  done(query: PageQuery, signal?: AbortSignal): Promise<TodoPage>;
  /** 我发起的：按发起时间从新到旧 */
  started(query: PageQuery, signal?: AbortSignal): Promise<FlowPage<ProcessInstance>>;
  /** 管理端搜实例：按发起时间从新到旧 */
  instances(query: PageQuery & InstanceSearch, signal?: AbortSignal): Promise<FlowPage<ProcessInstance>>;
  /** 管理端的流程列表 */
  processes(signal?: AbortSignal): Promise<readonly ProcessSummary[]>;
  /** 重试用完的作业 */
  failedJobs(query: PageQuery, signal?: AbortSignal): Promise<FlowPage<FailedJob>>;
  /** 抄送我的：未读在前 */
  ccList(query: PageQuery, signal?: AbortSignal): Promise<FlowPage<CcItem>>;
  /** 待办详情 */
  workItem(id: Id, signal?: AbortSignal): Promise<WorkItemView>;
  /** 实例详情 */
  instance(id: Id, signal?: AbortSignal): Promise<InstanceDetail>;
  /** 发起 */
  start(command: StartCommand): Promise<CommandResult>;
  /** 认领 */
  claim(workItemId: Id): Promise<CommandResult>;
  /** 处理 */
  complete(command: CompleteCommand): Promise<CommandResult>;
  /** 撤回 */
  withdraw(command: WithdrawCommand): Promise<CommandResult>;
  /** 转办：交给别人，自己不再参与 */
  transfer(command: HandOverCommand): Promise<CommandResult>;
  /** 委派：请别人先办，办完回到自己手里 */
  delegate(command: HandOverCommand): Promise<CommandResult>;
  /** 加签 */
  addSigners(command: AddSignersCommand): Promise<CommandResult>;
  /** 减签 */
  removeSigners(command: RemoveSignersCommand): Promise<CommandResult>;
  /** 退回收件箱 */
  release(command: ReleaseCommand): Promise<CommandResult>;
  /** 催办 */
  remind(command: RemindCommand): Promise<CommandResult>;
  /** 留言 */
  comment(command: CommentCommand): Promise<CommandResult>;
  /** 抄送（知会） */
  cc(command: CcCommand): Promise<CommandResult>;
  /** 外部事件（业务系统一般从后端直接调引擎；这里给前端的调试、演示用）：返回每个往下走的实例这一步的结果 */
  signal(command: SignalCommand): Promise<{ readonly results: readonly CommandResult[] }>;
  /** 读了抄送：一个实例抄送给我的都标成已读，返回标了几条 */
  readCc(instanceId: Id): Promise<{ readonly read: number }>;
  /** 终止（管理员） */
  terminate(command: InstanceCommand): Promise<CommandResult>;
  /** 暂停（管理员） */
  suspend(command: InstanceCommand): Promise<CommandResult>;
  /** 恢复（管理员） */
  resume(instanceId: Id): Promise<CommandResult>;
  /** 跳转（管理员） */
  jump(command: JumpCommand): Promise<CommandResult>;
  /** 改派（管理员、收件箱负责人） */
  reassign(command: HandOverCommand): Promise<CommandResult>;
  /** 失败的作业重新来过（管理员）：异步的自动节点重试用完后 */
  retryJob(jobId: Id): Promise<{ readonly revived: boolean }>;
}

/** createFlowClient 的选项 */
export interface FlowClientOptions {
  /** 接口的前缀，同后端的 flow.http.path；默认 /flow */
  prefix?: string;
}

/** 在一个请求客户端上建引擎的客户端 */
export function createFlowClient(http: HttpClient, { prefix = '/flow' }: FlowClientOptions = {}): FlowClient {
  const get = <T>(path: string, signal?: AbortSignal, query?: Record<string, number | string>) =>
    http.request<T>({ url: prefix + path, ...(signal ? { signal } : {}), ...(query ? { query } : {}) });
  const post = <T>(path: string, body: unknown) => http.request<T>({ url: prefix + path, method: 'POST', body });
  return {
    startable: (signal) => get('/queries/startable', signal),
    startView: (key, signal) => get(`/queries/start/${encodeURIComponent(key)}`, signal),
    todo: ({ page, pageSize }, signal) => get('/queries/todo', signal, { page, pageSize }),
    done: ({ page, pageSize }, signal) => get('/queries/done', signal, { page, pageSize }),
    started: ({ page, pageSize }, signal) => get('/queries/started', signal, { page, pageSize }),
    ccList: ({ page, pageSize }, signal) => get('/queries/cc', signal, { page, pageSize }),
    instances: ({ page, pageSize, ...search }, signal) => {
      // 空的条件不发：后端按"不限"处理
      const filters = Object.fromEntries(Object.entries(search).filter(([, v]) => v != null && v !== '')) as Record<string, string>;
      return get('/queries/instances', signal, { page, pageSize, ...filters });
    },
    processes: (signal) => get('/queries/processes', signal),
    failedJobs: ({ page, pageSize }, signal) => get('/queries/failed-jobs', signal, { page, pageSize }),
    workItem: (id, signal) => get(`/queries/work-items/${id}`, signal),
    instance: (id, signal) => get(`/queries/instances/${id}`, signal),
    start: (command) => post('/commands/start', command),
    claim: (workItemId) => post('/commands/claim', { workItemId }),
    complete: (command) => post('/commands/complete', command),
    withdraw: (command) => post('/commands/withdraw', command),
    transfer: (command) => post('/commands/transfer', command),
    delegate: (command) => post('/commands/delegate', command),
    addSigners: (command) => post('/commands/add-signers', command),
    removeSigners: (command) => post('/commands/remove-signers', command),
    release: (command) => post('/commands/release', command),
    remind: (command) => post('/commands/remind', command),
    comment: (command) => post('/commands/comment', command),
    cc: (command) => post('/commands/cc', command),
    signal: (command) => post('/commands/signal', command),
    readCc: (instanceId) => post('/commands/read-cc', { instanceId }),
    terminate: (command) => post('/admin/terminate', command),
    suspend: (command) => post('/admin/suspend', command),
    resume: (instanceId) => post('/admin/resume', { instanceId }),
    jump: (command) => post('/admin/jump', command),
    reassign: (command) => post('/admin/reassign', command),
    retryJob: (jobId) => post('/admin/retry-job', { jobId }),
  };
}

const FLOW_CLIENT: InjectionKey<FlowClient> = Symbol('flow-client');

/** 给下面的流程组件一个客户端（应用根上调一次） */
export function provideFlowClient(client: FlowClient): void {
  provide(FLOW_CLIENT, client);
}

/** 流程组件取客户端：provideFlowClient 给的，没给时用组件库配置里的请求客户端、前缀 /flow */
export function useFlowClient(): FlowClient {
  return inject(FLOW_CLIENT, null) ?? createFlowClient(useHttp());
}
