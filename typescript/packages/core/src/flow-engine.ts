/**
 * 流程引擎的门面（docs/decisions/0006，同 Java 的 FlowEngine）：接入方只和它打交道。命令改状态，查询读，扩展点在创建时给出。
 *
 *   const engine = createFlowEngine({ driver: mysqlDriver(pool), org });
 *   await engine.publishForm(admin, { key: 'purchase-form', name: '采购申请单', manifest });
 *   await engine.publishDefinition(admin, definitionJson, '第一版');
 *   const started = await engine.start(user, { key: 'purchase', form: { title: '办公桌', amount: '1200.00' } });
 *   await engine.complete(user, { workItemId: started.created[0]!.id, outcome: 'approve' });
 *
 * - 表由接入方建：执行本包 sql/mysql 下的脚本（同 Java flow-core 的 db/flow/mysql）；
 * - 每个命令在一个事务里执行（外面已有同一个库的事务时加入）；出错时抛带业务错误码的 FlowError（0008）；
 * - 每个操作先问 permissionPolicy；命令过了权限再过 interceptors（审计、业务校验、改参数），和标准处理在同一个事务里。
 */
import { FlowError, type CcItem, type Id, type ProcessInstance, type TodoItem } from '@zerodep-dev/flow-model';
import type { Driver, Page } from '@zerodep-dev/query-core';
import { weekdays, type WorkCalendar } from './calendar.js';
import type {
  AddSignersCommand,
  AssignCommand,
  CcCommand,
  ClaimCommand,
  CommandResult,
  CommentCommand,
  CompleteCommand,
  FormPublication,
  HandOverCommand,
  InstanceCommand,
  JumpCommand,
  ReadCcCommand,
  ReleaseCommand,
  RemindCommand,
  RemoveSignersCommand,
  RetryJobCommand,
  SkipJobCommand,
  StartCommand,
  WithdrawCommand,
} from './command/types.js';
import { jump, reassign, resume, retryJob, skipJob, suspend, terminate } from './engine/admin.js';
import { addSigners, assign, cc, comment, delegate, readCc, release, remind, removeSigners, transfer } from './engine/collaboration.js';
import { builtinJobs, JobRunner, type JobHandler } from './engine/jobs.js';
import { signal, type SignalCommand } from './engine/signals.js';
import { invalid } from './engine/errors.js';
import { EventDispatcher, inTransaction, type EngineContext, type FlowListener } from './engine/context.js';
import { DefinitionCache } from './engine/definitions.js';
import { claim, complete, start, withdraw } from './engine/handlers.js';
import { publishDefinition, publishForm, setFormStatus, setProcessStatus, validateDefinition } from './engine/publishing.js';
import { Queries } from './engine/queries.js';
import { withdrawStep, type WithdrawStepCommand } from './engine/step-withdrawal.js';
import { Extensions, type OutcomeGuard, type PeopleResolver, type ServiceAction } from './extensions.js';
import { allowAll, type Actor, type OrgDirectory, type PermissionPolicy } from './identity.js';
import { flowDatabase } from './store/query/database.js';
import { queryStores } from './store/query/stores.js';
import type { FormVersionInfo, InstanceSearch, StoredDefinition, StoredForm, Stores, VersionInfo } from './store/types.js';
import { instantOf, plusSeconds, systemClock, type Clock } from './time.js';
import type {
  DefinitionProblem,
  FormDocument,
  FormSummary,
  IncidentView,
  InstanceDetail,
  ProcessSummary,
  StartableProcess,
  StartView,
  WorkItemView,
} from './view/types.js';

/**
 * 命令拦截器（0006 第 6 节"管道"）：每个命令在权限检查之后经过它，和命令的标准处理在同一个事务里——审计、业务校验、改参数，
 * 或者整个换掉某个命令的标准行为（不调 proceed，自己做）。几个按给出的先后套起来，先给的在外层。
 *
 *   interceptors: [async (actor, operation, command, proceed) => {
 *     if (operation === 'complete' && blocked(command)) throw new FlowError('flow.validation-failed', …);
 *     const result = await proceed(command);   // 照常做（可以换成改过参数的命令）
 *     await audit(actor, operation, command);   // 抛错时整个命令回滚
 *     return result;
 *   }]
 */
export type CommandInterceptor<U extends Actor> = (
  actor: U,
  operation: string,
  command: object,
  proceed: (command: object) => Promise<unknown>,
) => Promise<unknown>;

/** 创建引擎时给的东西 */
export interface FlowEngineOptions<U extends Actor> {
  /** 数据库驱动（mysql2 的连接池经 query-mysql 的 `mysqlDriver(pool)`） */
  readonly driver: Driver;
  /** 接入方的组织架构（必须给：引擎没有默认的组织架构） */
  readonly org: OrgDirectory;
  /** 每个操作前的权限判断；默认全部放行 */
  readonly permissionPolicy?: PermissionPolicy<U>;
  readonly interceptors?: readonly CommandInterceptor<U>[];
  /** 时钟；默认系统时钟，测试里用固定时钟 */
  readonly clock?: Clock;
  /** 当前租户；默认不用多租户（0） */
  readonly tenant?: () => bigint;
  /** 工作日历；默认周一到周五、UTC 的日期 */
  readonly calendar?: WorkCalendar;
  readonly listeners?: readonly FlowListener[];
  readonly resolvers?: readonly PeopleResolver[];
  readonly guards?: readonly OutcomeGuard[];
  readonly actions?: readonly ServiceAction[];
  /** 新的作业种类，或换掉内置的 timer、overdue、remind、unclaimed、action、receiveTimeout */
  readonly jobHandlers?: readonly JobHandler[];
  /** 换掉默认存储里的几个 */
  readonly stores?: Partial<Stores>;
}

/** 创建引擎 */
export function createFlowEngine<U extends Actor>(options: FlowEngineOptions<U>): FlowEngine<U> {
  return new FlowEngine(options);
}

/** 流程引擎 */
export class FlowEngine<U extends Actor> {
  readonly #context: EngineContext;
  readonly #queries: Queries;
  readonly #permissions: PermissionPolicy<U>;
  readonly #interceptors: readonly CommandInterceptor<U>[];
  readonly #jobs: JobRunner;

  constructor(options: FlowEngineOptions<U>) {
    const clock = options.clock ?? systemClock;
    const db = flowDatabase(options.driver, options.tenant ?? (() => 0n), clock);
    const stores: Stores = { ...queryStores(db), ...options.stores };
    this.#context = {
      db,
      stores,
      clock,
      org: options.org,
      calendar: options.calendar ?? weekdays(),
      dispatcher: new EventDispatcher(options.listeners ?? []),
      extensions: new Extensions(options.resolvers, options.guards, options.actions),
      definitions: new DefinitionCache(stores),
    };
    this.#queries = new Queries(this.#context);
    this.#permissions = options.permissionPolicy ?? allowAll;
    this.#interceptors = options.interceptors ?? [];
    // 内置的作业在前，接入方注册的在后：同一种时接入方的换掉内置的
    this.#jobs = new JobRunner(this.#context, [...builtinJobs(this.#context), ...(options.jobHandlers ?? [])]);
  }

  // ───────── 发布 ─────────

  /** 发布流程定义：校验不通过时报 `flow.definition-invalid`，错误明细里是问题列表；版本号由后端给 */
  publishDefinition(actor: U, json: string, note: string | null = null): Promise<StoredDefinition> {
    this.#allow(actor, 'definitions.publish', null);
    return publishDefinition(this.#context, actor, json, note);
  }

  /** 校验一份定义、不发布：错误与警告都列出来 */
  validateDefinition(actor: U, json: string): Promise<DefinitionProblem[]> {
    this.#allow(actor, 'definitions.validate', null);
    return validateDefinition(this.#context, json);
  }

  /** 停用或启用流程：停用后不能再发起，进行中的照常走完 */
  setProcessActive(actor: U, processKey: string, active: boolean): Promise<ProcessSummary> {
    this.#allow(actor, 'definitions.status', processKey);
    return setProcessStatus(this.#context, processKey, active);
  }

  /** 流程的全部版本，新的在前 */
  versions(actor: U, processKey: string): Promise<VersionInfo[]> {
    this.#allow(actor, 'definitions.versions', processKey);
    return this.#queries.versions(processKey);
  }

  /** 某个版本的定义 JSON（发布时的原样）；version 为 null 时取最新的 */
  definitionJson(actor: U, processKey: string, version: number | null = null): Promise<string> {
    this.#allow(actor, 'definitions.read', processKey);
    return this.#queries.definitionJson(processKey, version);
  }

  /** 管理端的表单列表：停用的也在，按分类、名称排 */
  forms(actor: U): Promise<FormSummary[]> {
    this.#allow(actor, 'forms.list', null);
    return this.#queries.forms();
  }

  /** 表单的全部版本，新的在前 */
  formVersions(actor: U, formKey: string): Promise<FormVersionInfo[]> {
    this.#allow(actor, 'forms.versions', formKey);
    return this.#queries.formVersions(formKey);
  }

  /** 表单某个版本的源码与字段清单（编辑时载入）；version 为 null 时取最新的 */
  formDocument(actor: U, formKey: string, version: number | null = null): Promise<FormDocument> {
    this.#allow(actor, 'forms.read', formKey);
    return this.#queries.formDocument(formKey, version);
  }

  /** 停用或启用表单：停用后新发布的流程不能再选它，已经引用它的照常用 */
  setFormActive(actor: U, formKey: string, active: boolean): Promise<FormSummary> {
    this.#allow(actor, 'forms.status', formKey);
    return setFormStatus(this.#context, formKey, active);
  }

  /** 发布表单：字段清单校验不通过时报 `flow.validation-failed` */
  publishForm(actor: U, form: FormPublication): Promise<StoredForm> {
    this.#allow(actor, 'forms.publish', form.key);
    return publishForm(this.#context, actor, form);
  }

  // ───────── 命令 ─────────

  /** 发起：按最新发布的版本走 */
  start(actor: U, command: StartCommand): Promise<CommandResult> {
    return this.#execute(actor, 'start', command.key, command, (a, c) => start(this.#context, a, c));
  }

  /** 认领收件箱里的待办：先到先得 */
  claim(actor: U, command: ClaimCommand): Promise<CommandResult> {
    return this.#execute(actor, 'claim', String(command.workItemId), command, (a, c) => claim(this.#context, a, c));
  }

  /** 处理一个待办 */
  complete(actor: U, command: CompleteCommand): Promise<CommandResult> {
    return this.#execute(actor, 'complete', String(command.workItemId), command, (a, c) => complete(this.#context, a, c));
  }

  /** 发起人撤回整个实例 */
  withdraw(actor: U, command: WithdrawCommand): Promise<CommandResult> {
    return this.#execute(actor, 'withdraw', String(command.instanceId), command, (a, c) => withdraw(this.#context, a, c));
  }

  /** 处理人撤回刚处理的一步：下一步还没人动时，待办回到自己手里 */
  withdrawStep(actor: U, command: WithdrawStepCommand): Promise<CommandResult> {
    return this.#execute(actor, 'withdrawStep', String(command.workItemId), command, (a, c) => withdrawStep(this.#context, a, c));
  }

  // ───────── 协作与管理员命令（0006 的 3.1–3.4） ─────────

  /** 转办：交给别人，自己不再参与 */
  transfer(actor: U, command: HandOverCommand): Promise<CommandResult> {
    return this.#execute(actor, 'transfer', String(command.workItemId), command, (a, c) => transfer(this.#context, a, c));
  }

  /** 委派：请别人先办，办完回到自己手里 */
  delegate(actor: U, command: HandOverCommand): Promise<CommandResult> {
    return this.#execute(actor, 'delegate', String(command.workItemId), command, (a, c) => delegate(this.#context, a, c));
  }

  /** 加签：前加签、后加签、并加 */
  addSigners(actor: U, command: AddSignersCommand): Promise<CommandResult> {
    return this.#execute(actor, 'addSigners', String(command.workItemId), command, (a, c) => addSigners(this.#context, a, c));
  }

  /** 减签：取消自己加的、还没办的人 */
  removeSigners(actor: U, command: RemoveSignersCommand): Promise<CommandResult> {
    return this.#execute(actor, 'removeSigners', String(command.workItemId), command, (a, c) => removeSigners(this.#context, a, c));
  }

  /** 退回收件箱：自己以后不能再认领这一条 */
  release(actor: U, command: ReleaseCommand): Promise<CommandResult> {
    return this.#execute(actor, 'release', String(command.workItemId), command, (a, c) => release(this.#context, a, c));
  }

  /** 催办：同一个待办一小时内只记一次 */
  remind(actor: U, command: RemindCommand): Promise<CommandResult> {
    return this.#execute(actor, 'remind', String(command.instanceId), command, (a, c) => remind(this.#context, a, c));
  }

  /** 留言：记进流转记录 */
  comment(actor: U, command: CommentCommand): Promise<CommandResult> {
    return this.#execute(actor, 'comment', String(command.instanceId), command, (a, c) => comment(this.#context, a, c));
  }

  /** 收件箱负责人指定承办人 */
  assign(actor: U, command: AssignCommand): Promise<CommandResult> {
    return this.#execute(actor, 'assign', String(command.workItemId), command, (a, c) => assign(this.#context, a, c));
  }

  /** 抄送（知会）：每人一条抄送，记进流转记录 */
  cc(actor: U, command: CcCommand): Promise<CommandResult> {
    return this.#execute(actor, 'cc', String(command.instanceId), command, (a, c) => cc(this.#context, a, c));
  }

  /** 读了抄送：一个实例抄送给我的都标成已读，返回标了几条 */
  readCc(actor: U, command: ReadCcCommand): Promise<number> {
    return this.#execute(actor, 'readCc', String(command.instanceId), command, (a, c) => readCc(this.#context, a, c));
  }

  /** 外部事件：在等它的实例往下走，返回每个实例这一步的结果 */
  signal(actor: U, command: SignalCommand): Promise<CommandResult[]> {
    const target = command.instanceId != null ? String(command.instanceId) : (command.businessKey ?? null);
    return this.#execute(actor, 'signal', target, command, (a, c) => signal(this.#context, a, c));
  }

  /** 终止（管理员）：打开着的待办都取消，实例结束 */
  terminate(actor: U, command: InstanceCommand): Promise<CommandResult> {
    return this.#execute(actor, 'terminate', String(command.instanceId), command, (a, c) => terminate(this.#context, a, c));
  }

  /** 暂停（管理员）：待办都不能处理，直到恢复 */
  suspend(actor: U, command: InstanceCommand): Promise<CommandResult> {
    return this.#execute(actor, 'suspend', String(command.instanceId), command, (a, c) => suspend(this.#context, a, c));
  }

  /** 恢复（管理员） */
  resume(actor: U, command: InstanceCommand): Promise<CommandResult> {
    return this.#execute(actor, 'resume', String(command.instanceId), command, (a, c) => resume(this.#context, a, c));
  }

  /** 跳转（管理员）：原来的待办取消，从指定的节点重新走 */
  jump(actor: U, command: JumpCommand): Promise<CommandResult> {
    return this.#execute(actor, 'jump', String(command.instanceId), command, (a, c) => jump(this.#context, a, c));
  }

  /** 改派（管理员、收件箱负责人）：待办直接落到这个人 */
  reassign(actor: U, command: HandOverCommand): Promise<CommandResult> {
    return this.#execute(actor, 'reassign', String(command.workItemId), command, (a, c) => reassign(this.#context, a, c));
  }

  /** 失败的作业重新来过（管理员）：修好外部系统后让异步的自动节点再做一次 */
  retryJob(actor: U, command: RetryJobCommand): Promise<boolean> {
    return this.#execute(actor, 'retryJob', String(command.jobId), command, (a, c) => retryJob(this.#context, a, c));
  }

  /** 跳过失败的作业（管理员）：异步的自动节点不再做动作，按指定的结果往下走 */
  skipJob(actor: U, command: SkipJobCommand): Promise<CommandResult> {
    return this.#execute(actor, 'skipJob', String(command.jobId), command, (a, c) => skipJob(this.#context, a, c));
  }

  // ───────── 作业、幂等 ─────────

  /**
   * 做一批到期的作业（定时节点到点、待办超时、提醒、无人认领、异步的自动节点），返回做了几个。Nuxt 里由插件定时调；
   * 几个进程同时调不会重复做。
   */
  runDueJobs(): Promise<number> {
    return this.#jobs.runDue();
  }

  /** 删掉过期的幂等记录（全部租户）：早于 olderSeconds 秒之前的请求编号不再认，返回删了几条 */
  purgeRequests(olderSeconds: number): Promise<number> {
    const cutoff = plusSeconds(instantOf(this.#context.clock.now()), -olderSeconds);
    return inTransaction(this.#context, () => this.#context.stores.requests.deleteBefore(cutoff));
  }

  /**
   * 幂等地做一个命令（0006 第 1 节 requestId，HTTP 的 Idempotency-Key）：这个请求编号没做过时做 work，把它给出的响应与命令在同一个事务里记下；
   * 做过时直接返回第一次的响应。同一个编号用在别的命令上、换了人时报 `flow.validation-failed`（requestId: reused）。
   */
  idempotent(actor: U, requestId: string, command: string, work: () => Promise<string>): Promise<string> {
    return inTransaction(this.#context, async () => {
      const done = await this.#context.stores.requests.find(requestId);
      if (done) {
        if (done.command !== command || done.actorId !== actor.id) throw invalid('requestId', 'reused');
        return done.response;
      }
      const response = await work();
      await this.#context.stores.requests.save({ requestId, command, actorId: actor.id, response, createdAt: instantOf(this.#context.clock.now()) });
      return response;
    });
  }

  // ───────── 查询 ─────────

  /** 异常：重试用完的作业，新的在前；instanceId 不为 null 时只看这个实例的 */
  incidents(actor: U, instanceId: Id | null = null, page = 1, size = 20): Promise<Page<IncidentView>> {
    this.#allow(actor, 'incidents', instanceId === null ? null : String(instanceId));
    return this.#queries.incidents(instanceId, page, size);
  }

  /** 我能发起的流程 */
  startable(actor: U): Promise<StartableProcess[]> {
    this.#allow(actor, 'startable', null);
    return this.#queries.startable(actor);
  }

  /** 发起页：流程与开始节点上的表单 */
  startView(actor: U, key: string): Promise<StartView> {
    this.#allow(actor, 'startView', key);
    return this.#queries.startView(actor, key);
  }

  /** 我的待办，按到达时间从新到旧 */
  todo(actor: U, page = 1, size = 20): Promise<Page<TodoItem>> {
    this.#allow(actor, 'todo', String(actor.id));
    return this.#queries.todo(actor, page, size);
  }

  /** 等我指定承办人的待办 */
  designations(actor: U, page = 1, size = 20): Promise<Page<TodoItem>> {
    this.#allow(actor, 'designations', String(actor.id));
    return this.#queries.designations(actor, page, size);
  }

  /** 我已办，按处理时间从新到旧 */
  done(actor: U, page = 1, size = 20): Promise<Page<TodoItem>> {
    this.#allow(actor, 'done', String(actor.id));
    return this.#queries.done(actor, page, size);
  }

  /** 我发起的，按发起时间从新到旧 */
  started(actor: U, page = 1, size = 20): Promise<Page<ProcessInstance>> {
    this.#allow(actor, 'started', String(actor.id));
    return this.#queries.started(actor, page, size);
  }

  /** 抄送我的：未读在前 */
  ccList(actor: U, page = 1, size = 20): Promise<Page<CcItem>> {
    this.#allow(actor, 'cc.list', String(actor.id));
    return this.#queries.ccList(actor, page, size);
  }

  /** 管理端搜实例 */
  instances(actor: U, search: InstanceSearch, page = 1, size = 20): Promise<Page<ProcessInstance>> {
    this.#allow(actor, 'instances.search', null);
    return this.#queries.instances(search, page, size);
  }

  /** 管理端的流程列表 */
  processes(actor: U): Promise<ProcessSummary[]> {
    this.#allow(actor, 'processes.list', null);
    return this.#queries.processes();
  }

  /** 待办详情 */
  workItem(actor: U, workItemId: Id): Promise<WorkItemView> {
    this.#allow(actor, 'workItem', String(workItemId));
    return this.#queries.workItem(actor, workItemId);
  }

  /** 实例详情 */
  instance(actor: U, instanceId: Id): Promise<InstanceDetail> {
    this.#allow(actor, 'instance', String(instanceId));
    return this.#queries.instance(actor, instanceId);
  }

  // ───────── 管道 ─────────

  /** 命令的管道：权限 → 在一个事务里依次过拦截器（先给的在外层）、做标准处理 */
  async #execute<C extends object, R>(actor: U, operation: string, target: string | null, command: C, handler: (actor: U, command: C) => Promise<R>): Promise<R> {
    this.#allow(actor, operation, target);
    if (this.#interceptors.length === 0) return handler(actor, command);
    const chain = (index: number): ((command: object) => Promise<unknown>) =>
      index === this.#interceptors.length
        ? (next) => handler(actor, next as C)
        : (next) => this.#interceptors[index]!(actor, operation, next, chain(index + 1));
    return inTransaction(this.#context, () => chain(0)(command) as Promise<R>);
  }

  #allow(actor: U, operation: string, target: string | null) {
    if (!this.#permissions(actor, operation, target)) throw new FlowError('flow.forbidden', { params: { operation, target: String(target) } });
  }
}
