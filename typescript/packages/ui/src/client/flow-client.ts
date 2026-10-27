/**
 * 引擎 HTTP 接口的客户端（0006 的 7.3）：一个命令一个方法，结果的类型在 types.ts。建在组件库的请求层上：
 * 响应体按 0008 拆包（出错时抛带业务错误码的 HttpError），typed JSON 还原（编号是 bigint）；地址栏参数里的 null、undefined
 * 由请求层略过，bigint、日期由它写成文字，这里不再处理。
 *
 *   const flow = createFlowClient(createHttpClient({ headers: { … } }));
 *   provideFlowClient(flow);                     // 应用根上给一次，流程组件里 useFlowClient() 取
 *   const view = await flow.workItem(id);
 *
 * 不给时组件取 `createFlowClient(useHttp())`：用组件库配置里的请求客户端，前缀 /flow。
 */
import type { CcItem, Id, ProcessInstance, TodoItem } from '@zerodep-dev/flow-model';
import { formatSorts, scopeHttp, useHttp, type HttpClient, type QueryValue, type SortState, type TablePage } from '@zerodep-dev/vue-tsx-pro-ui';
import { inject, provide, type InjectionKey } from 'vue';
import type {
  AddSignersCommand,
  CcCommand,
  CommandResult,
  DefinitionProblem,
  DefinitionVersion,
  FormDocument,
  FormPublication,
  FormSummary,
  FormUsage,
  FormVersion,
  Incident,
  InstanceSearch,
  ProcessSummary,
  PublishedDefinition,
  BatchResult,
  WorkCalendar,
  MigrateCommand,
  SaveStartDraftCommand,
  ProgressView,
  ProcessStats,
  NodeStats,
  HandlerStats,
  ChangeDataCommand,
  CompileProblem,
  DesignDraft,
  FormActionCommand,
  DraftKind,
  SaveDesignDraftCommand,
  PublishedForm,
  SignalCommand,
  SkipJobCommand,
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
  SaveDraftCommand,
  StartCommand,
  StartView,
  WithdrawCommand,
  WorkItemView,
} from './types';

/** 一页：组件库表格的 TablePage，加上页码、每页条数、总页数（后端的 Page） */
export interface FlowPage<T> extends TablePage<T> {
  readonly page: number;
  readonly pageSize: number;
  readonly totalPages: number;
}

/** 一页待办 */
export type TodoPage = FlowPage<TodoItem>;

/** 我的待办、我已办、我发起的、抄送我的的筛选：流程、起止时刻（ISO）、接入方的参数 */
export interface ListQuery {
  readonly processKey?: string;
  readonly from?: string;
  readonly to?: string;
  /** 只看已归档的（我已办、我发起的、抄送我的） */
  readonly archived?: boolean;
  /** 接入方自己的筛选参数（后端的 TodoQueryCustomizer 收到，只对我的待办、我已办）：发成查询串里 biz. 开头的参数 */
  readonly business?: Readonly<Record<string, string>>;
}

/** 分页与排序的参数：同组件库表格的请求（useTableQuery） */
export interface PageQuery {
  readonly page: number;
  readonly pageSize: number;
  /**
   * 排序：直接给组件库表格的 q.sorts，发出去时写成 formatSorts 的文字（`-startedAt,title`）；空的是默认顺序。
   * 后端每个列表只认自己的几列（我发起的、管理端搜实例、我已办、抄送我的；我的待办不排）
   */
  readonly sorts?: readonly SortState[];
}

/** 列表查询的参数：接入方的筛选参数展开成 biz.名字（后端的 TodoQueryCustomizer 收到去掉前缀的） */
function listParams({ business, sorts, ...rest }: PageQuery & ListQuery): Record<string, QueryValue> {
  const out: Record<string, QueryValue> = { ...rest, ...(sorts?.length ? { sort: formatSorts(sorts) } : {}) };
  for (const [name, value] of Object.entries(business ?? {})) out[`biz.${name}`] = value;
  return out;
}

/** 引擎接口的全部方法 */
export interface FlowClient {
  /** 我能发起的流程 */
  startable(signal?: AbortSignal): Promise<readonly StartableProcess[]>;
  /** 发起页：流程与开始节点上的表单 */
  startView(key: string, signal?: AbortSignal, draftId?: Id | null): Promise<StartView>;
  /** 发起前存草稿：返回草稿（状态为 draft 的实例），startedAt 是最后存的时间 */
  saveStartDraft(command: SaveStartDraftCommand): Promise<ProcessInstance>;
  /** 放弃发起前的草稿 */
  discardStartDraft(draftId: Id): Promise<{ readonly discarded: boolean }>;
  /** 我发起前存的草稿，最后存的在前 */
  startDrafts(signal?: AbortSignal): Promise<readonly ProcessInstance[]>;
  /** 我的待办 */
  todo(query: PageQuery & Pick<ListQuery, 'processKey' | 'business'>, signal?: AbortSignal): Promise<TodoPage>;
  /** 等我指定承办人的（没人认领、我是收件箱负责人） */
  designations(query: PageQuery, signal?: AbortSignal): Promise<TodoPage>;
  /** 我已办：按处理时间从新到旧 */
  done(query: PageQuery & ListQuery, signal?: AbortSignal): Promise<TodoPage>;
  /** 我发起的：按发起时间从新到旧 */
  started(query: PageQuery & ListQuery, signal?: AbortSignal): Promise<FlowPage<ProcessInstance>>;
  /** 管理端搜实例：按发起时间从新到旧 */
  instances(query: PageQuery & InstanceSearch, signal?: AbortSignal): Promise<FlowPage<ProcessInstance>>;
  /** 管理端的流程列表 */
  processes(signal?: AbortSignal): Promise<readonly ProcessSummary[]>;
  /** 异常（重试用完的作业），新的在前；给 instanceId 时只看这个实例的 */
  incidents(query: PageQuery & { readonly instanceId?: Id }, signal?: AbortSignal): Promise<FlowPage<Incident>>;
  /** 流程的全部版本，新的在前 */
  versions(processKey: string, signal?: AbortSignal): Promise<readonly DefinitionVersion[]>;
  /** 某个版本的定义（发布时的原文）；version 不给时取最新的 */
  definitionJson(processKey: string, version?: number, signal?: AbortSignal): Promise<{ readonly json: string }>;
  /** 试编译表单的后端代码（Java）：没有问题时是空的 */
  compileFormCode(source: string): Promise<readonly CompileProblem[]>;
  /** 调表单后端代码给前端的接口（表单代码里的 context.call） */
  formAction(command: FormActionCommand): Promise<unknown>;
  /** 设计中的草稿；没有时为 null */
  designDraft(kind: DraftKind, key: string, signal?: AbortSignal): Promise<DesignDraft | null>;
  /** 某一种的全部草稿（不带内容）：列表上标"有草稿" */
  designDrafts(kind: DraftKind, signal?: AbortSignal): Promise<readonly DesignDraft[]>;
  /** 保存草稿，返回存好的（带新的 rev） */
  saveDesignDraft(command: SaveDesignDraftCommand): Promise<DesignDraft>;
  /** 放弃草稿 */
  discardDesignDraft(kind: DraftKind, key: string): Promise<void>;
  /** 校验定义、不发布：错误与警告都列出来；json 是定义的原文（写错了也照样发，后端报 malformed） */
  validateDefinition(json: string): Promise<{ readonly problems: readonly DefinitionProblem[] }>;
  /** 发布定义：有错误时报 flow.definition-invalid，明细是问题列表 */
  publishDefinition(json: string, note?: string): Promise<PublishedDefinition>;
  /** 停用或启用流程 */
  setProcessActive(processKey: string, active: boolean): Promise<ProcessSummary>;
  /** 管理端的表单列表（停用的也在） */
  forms(signal?: AbortSignal): Promise<readonly FormSummary[]>;
  /** 表单的全部版本，新的在前 */
  formVersions(formKey: string, signal?: AbortSignal): Promise<readonly FormVersion[]>;
  /** 用了这张表单的流程（表单设计器按节点的权限预览） */
  formUsages(formKey: string, signal?: AbortSignal): Promise<readonly FormUsage[]>;
  /** 表单某个版本的源码与字段清单；version 不给时取最新的 */
  formDocument(formKey: string, version?: number, signal?: AbortSignal): Promise<FormDocument>;
  /** 发布表单：清单不合规则时报 flow.validation-failed */
  publishForm(form: FormPublication): Promise<PublishedForm>;
  /** 停用、启用表单：停用后新发布的流程不能再选它 */
  setFormActive(formKey: string, active: boolean): Promise<FormSummary>;
  /** 抄送我的：未读在前 */
  ccList(query: PageQuery & ListQuery, signal?: AbortSignal): Promise<FlowPage<CcItem>>;
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
  /** 暂存：填了一半先存在待办上，不提交 */
  saveDraft(command: SaveDraftCommand): Promise<CommandResult>;
  /** 撤回 */
  withdraw(command: WithdrawCommand): Promise<CommandResult>;
  /** 申请改派：负责人指定给我的待办回到他那里重新指定 */
  requestReassign(workItemId: Id, reason: string): Promise<CommandResult>;
  /** 批量认领：每个待办一个结果 */
  batchClaim(workItemIds: readonly Id[]): Promise<{ readonly results: readonly BatchResult[] }>;
  /** 批量处理：按同一个结果；不写结果时各按第一个"同意"类的 */
  batchComplete(workItemIds: readonly Id[], outcome?: string, comment?: string): Promise<{ readonly results: readonly BatchResult[] }>;
  /** 管理员把实例迁到流程的另一个版本 */
  migrate(command: MigrateCommand): Promise<CommandResult>;
  /** 外部参与方看的进度 */
  progress(instanceId: Id, signal?: AbortSignal): Promise<ProgressView>;
  /** 统计：各流程的实例数 */
  processStats(signal?: AbortSignal): Promise<readonly ProcessStats[]>;
  /** 统计：一个流程各节点的停留与超时（since 是 ISO 时刻，不写取 30 天前） */
  nodeStats(processKey: string, since?: string, signal?: AbortSignal): Promise<readonly NodeStats[]>;
  /** 统计：处理人的工作量 */
  handlerStats(since?: string, signal?: AbortSignal): Promise<readonly HandlerStats[]>;
  /** 全部工作日历 */
  calendars(signal?: AbortSignal): Promise<readonly WorkCalendar[]>;
  /** 保存一份工作日历（特殊日子整份换掉） */
  saveCalendar(calendar: WorkCalendar): Promise<WorkCalendar>;
  /** 删掉一份工作日历 */
  deleteCalendar(name: string): Promise<void>;
  /** 管理员改流程变量、表单字段 */
  changeData(command: ChangeDataCommand): Promise<CommandResult>;
  /** 撤回刚处理的一步：下一步还没人动时，待办回到自己手里 */
  withdrawStep(workItemId: Id, reason?: string): Promise<CommandResult>;
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
  /** 收件箱负责人指定承办人 */
  assign(workItemId: Id, userId: Id): Promise<CommandResult>;
  /** 外部事件（业务系统一般从后端直接调引擎；这里给前端的调试、演示用）：返回每个往下走的实例这一步的结果 */
  signal(command: SignalCommand): Promise<{ readonly results: readonly CommandResult[] }>;
  /** 读了抄送：一个实例抄送给我的都标成已读，返回标了几条 */
  readCc(instanceId: Id): Promise<{ readonly read: number }>;
  /** 打开了自己的待办：第一次打开时记下时间（列表里不再标"新"）；不是自己的、读过的不动 */
  readWorkItem(workItemId: Id): Promise<{ readonly read: boolean }>;
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
  /** 跳过失败的作业（管理员）：自动节点不再做动作，按指定的结果往下走 */
  skipJob(command: SkipJobCommand): Promise<CommandResult>;
}

/** createFlowClient 的选项 */
export interface FlowClientOptions {
  /** 接口的前缀，同后端的 flow.http.path；默认 /flow */
  prefix?: string;
}

/** 在一个请求客户端上建引擎的客户端 */
export function createFlowClient(http: HttpClient, { prefix = '/flow' }: FlowClientOptions = {}): FlowClient {
  const api = scopeHttp(http, prefix);
  const get = <T>(path: string, signal?: AbortSignal, query?: Readonly<Record<string, QueryValue>>) =>
    api.get<T>(path, { ...(signal ? { signal } : {}), ...(query ? { query } : {}) });
  const post = <T>(path: string, body: unknown) => api.post<T>(path, body);
  /** 地址里的一段（流程、表单的标识可能有斜杠以外的特殊字） */
  const seg = encodeURIComponent;
  return {
    startable: (signal) => get('/queries/startable', signal),
    startView: (key, signal, draftId) => get(`/queries/start/${seg(key)}`, signal, { draftId }),
    saveStartDraft: (command) => post('/commands/save-start-draft', command),
    discardStartDraft: (draftId) => post('/commands/discard-start-draft', { draftId }),
    startDrafts: (signal) => get('/queries/start-drafts', signal),
    todo: (query, signal) => get('/queries/todo', signal, listParams(query)),
    done: (query, signal) => get('/queries/done', signal, listParams(query)),
    designations: ({ page, pageSize }, signal) => get('/queries/designations', signal, { page, pageSize }),
    started: (query, signal) => get('/queries/started', signal, listParams(query)),
    ccList: (query, signal) => get('/queries/cc', signal, listParams(query)),
    instances: ({ page, pageSize, sorts, ...search }, signal) => {
      // 空的文字也不发（null、undefined 请求层会略过）：后端按"不限"处理
      const filters = Object.fromEntries(Object.entries(search).filter(([, v]) => v !== '')) as Record<string, QueryValue>;
      return get('/queries/instances', signal, { ...listParams({ page, pageSize, ...(sorts ? { sorts } : {}) }), ...filters });
    },
    processes: (signal) => get('/queries/processes', signal),
    incidents: ({ page, pageSize, instanceId }, signal) =>
      get('/queries/incidents', signal, { page, pageSize, instanceId }),
    versions: (processKey, signal) => get(`/queries/processes/${seg(processKey)}/versions`, signal),
    compileFormCode: (source) => post('/forms/compile', { source }),
    formAction: (command) => post('/forms/actions', command),
    designDraft: (kind, key, signal) => get(`/queries/drafts/${kind}/${seg(key)}`, signal),
    designDrafts: (kind, signal) => get(`/queries/drafts/${kind}`, signal),
    saveDesignDraft: (command) => post('/drafts/save', command),
    discardDesignDraft: async (kind, key) => {
      await post('/drafts/discard', { kind, key });
    },
    definitionJson: (processKey, version, signal) =>
      get(`/queries/processes/${seg(processKey)}/definition`, signal, { version }),
    // 定义的原文按文字发（不经 JSON 序列化）：写错的 JSON 也让后端报出位置
    validateDefinition: (json) => post('/definitions/validate', json),
    publishDefinition: (json, note) => api.post('/definitions/publish', json, { query: { note } }),
    setProcessActive: (processKey, active) => post(`/definitions/${active ? 'enable' : 'disable'}`, { key: processKey }),
    forms: (signal) => get('/queries/forms', signal),
    formVersions: (formKey, signal) => get(`/queries/forms/${seg(formKey)}/versions`, signal),
    formUsages: (formKey, signal) => get(`/queries/forms/${seg(formKey)}/usages`, signal),
    formDocument: (formKey, version, signal) =>
      get(`/queries/forms/${seg(formKey)}/document`, signal, { version }),
    publishForm: (form) => post('/forms/publish', form),
    setFormActive: (formKey, active) => post(`/forms/${active ? 'enable' : 'disable'}`, { key: formKey }),
    workItem: (id, signal) => get(`/queries/work-items/${id}`, signal),
    instance: (id, signal) => get(`/queries/instances/${id}`, signal),
    start: (command) => post('/commands/start', command),
    claim: (workItemId) => post('/commands/claim', { workItemId }),
    complete: (command) => post('/commands/complete', command),
    saveDraft: (command) => post('/commands/save-draft', command),
    withdraw: (command) => post('/commands/withdraw', command),
    requestReassign: (workItemId, reason) => post('/commands/request-reassign', { workItemId, reason }),
    batchClaim: (workItemIds) => post('/commands/batch-claim', { workItemIds }),
    batchComplete: (workItemIds, outcome, comment) =>
      post('/commands/batch-complete', { workItemIds, ...(outcome ? { outcome } : {}), ...(comment ? { comment } : {}) }),
    changeData: (command) => post('/admin/change-data', command),
    migrate: (command) => post('/admin/migrate', command),
    progress: (instanceId, signal) => get(`/queries/instances/${instanceId}/progress`, signal),
    processStats: (signal) => get('/queries/stats/processes', signal),
    nodeStats: (processKey, since, signal) => get('/queries/stats/nodes', signal, { processKey, since }),
    handlerStats: (since, signal) => get('/queries/stats/handlers', signal, { since }),
    calendars: (signal) => get('/queries/calendars', signal),
    saveCalendar: (calendar) => post('/calendars/save', calendar),
    deleteCalendar: async (name) => {
      await post('/calendars/delete', { name });
    },
    withdrawStep: (workItemId, reason) => post('/commands/withdraw-step', { workItemId, ...(reason ? { reason } : {}) }),
    transfer: (command) => post('/commands/transfer', command),
    delegate: (command) => post('/commands/delegate', command),
    addSigners: (command) => post('/commands/add-signers', command),
    removeSigners: (command) => post('/commands/remove-signers', command),
    release: (command) => post('/commands/release', command),
    remind: (command) => post('/commands/remind', command),
    comment: (command) => post('/commands/comment', command),
    cc: (command) => post('/commands/cc', command),
    assign: (workItemId, userId) => post('/commands/assign', { workItemId, userId }),
    signal: (command) => post('/commands/signal', command),
    readCc: (instanceId) => post('/commands/read-cc', { instanceId }),
    readWorkItem: (workItemId) => post('/commands/read-work-item', { workItemId }),
    terminate: (command) => post('/admin/terminate', command),
    suspend: (command) => post('/admin/suspend', command),
    resume: (instanceId) => post('/admin/resume', { instanceId }),
    jump: (command) => post('/admin/jump', command),
    reassign: (command) => post('/admin/reassign', command),
    retryJob: (jobId) => post('/admin/retry-job', { jobId }),
    skipJob: (command) => post('/admin/skip-job', command),
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
