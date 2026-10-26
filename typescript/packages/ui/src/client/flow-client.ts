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
import type { Id, TodoItem } from '@zerodep-dev/flow-model';
import { useHttp, type HttpClient, type TablePage } from '@zerodep-dev/vue-tsx-pro-ui';
import { inject, provide, type InjectionKey } from 'vue';
import type {
  CommandResult,
  CompleteCommand,
  InstanceDetail,
  StartableProcess,
  StartCommand,
  StartView,
  WithdrawCommand,
  WorkItemView,
} from './types';

/** 一页待办 */
export interface TodoPage extends TablePage<TodoItem> {
  readonly page: number;
  readonly size: number;
}

/** 引擎接口的全部方法 */
export interface FlowClient {
  /** 我能发起的流程 */
  startable(signal?: AbortSignal): Promise<readonly StartableProcess[]>;
  /** 发起页：流程与开始节点上的表单 */
  startView(key: string, signal?: AbortSignal): Promise<StartView>;
  /** 我的待办 */
  todo(query: { page: number; pageSize: number }, signal?: AbortSignal): Promise<TodoPage>;
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
}

/** createFlowClient 的选项 */
export interface FlowClientOptions {
  /** 接口的前缀，同后端的 flow.http.path；默认 /flow */
  prefix?: string;
}

/** 在一个请求客户端上建引擎的客户端 */
export function createFlowClient(http: HttpClient, { prefix = '/flow' }: FlowClientOptions = {}): FlowClient {
  const get = <T>(path: string, signal?: AbortSignal, query?: Record<string, number>) =>
    http.request<T>({ url: prefix + path, ...(signal ? { signal } : {}), ...(query ? { query } : {}) });
  const post = <T>(path: string, body: unknown) => http.request<T>({ url: prefix + path, method: 'POST', body });
  return {
    startable: (signal) => get('/queries/startable', signal),
    startView: (key, signal) => get(`/queries/start/${encodeURIComponent(key)}`, signal),
    todo: ({ page, pageSize }, signal) => get('/queries/todo', signal, { page, pageSize }),
    workItem: (id, signal) => get(`/queries/work-items/${id}`, signal),
    instance: (id, signal) => get(`/queries/instances/${id}`, signal),
    start: (command) => post('/commands/start', command),
    claim: (workItemId) => post('/commands/claim', { workItemId }),
    complete: (command) => post('/commands/complete', command),
    withdraw: (command) => post('/commands/withdraw', command),
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
