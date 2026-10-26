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
import type { ClaimCommand, CommandResult, CompleteCommand, FormPublication, StartCommand, WithdrawCommand } from './command/types.js';
import { EventDispatcher, inTransaction, type EngineContext, type FlowListener } from './engine/context.js';
import { DefinitionCache } from './engine/definitions.js';
import { claim, complete, start, withdraw } from './engine/handlers.js';
import { publishDefinition, publishForm, setProcessStatus, validateDefinition } from './engine/publishing.js';
import { Queries } from './engine/queries.js';
import { withdrawStep, type WithdrawStepCommand } from './engine/step-withdrawal.js';
import { Extensions, type OutcomeGuard, type PeopleResolver, type ServiceAction } from './extensions.js';
import { allowAll, type Actor, type OrgDirectory, type PermissionPolicy } from './identity.js';
import { flowDatabase } from './store/query/database.js';
import { queryStores } from './store/query/stores.js';
import type { InstanceSearch, StoredDefinition, StoredForm, Stores, VersionInfo } from './store/types.js';
import { systemClock, type Clock } from './time.js';
import type { DefinitionProblem, InstanceDetail, ProcessSummary, StartableProcess, StartView, WorkItemView } from './view/types.js';

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

  // ───────── 查询 ─────────

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
