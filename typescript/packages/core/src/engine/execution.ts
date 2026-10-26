/**
 * 一个命令里对一个实例的推进（同 Java 的 Execution）：让实例从某个节点往前走，直到停在人工节点（等人处理）或走到结束。
 * <pre>
 *   命令的处理（start、complete……）：锁住实例、核对参数、收表单
 *     ▼
 *   leave(节点, 结果) ──► 按结果与条件选线（selectEdges），线上的副作用改流程变量，enter(下一个节点)
 *                           ├─ 人工节点：轮次加一，解析处理人，生成待办（有时限的排上超时、提醒的作业）──► 停下
 *                           ├─ 定时节点：记下到什么时候，排一个作业 ──► 停下
 *                           ├─ 抄送节点：抄送给写的人 ──► 接着走
 *                           ├─ 自动节点：同步的马上做动作、按结果接着走；异步的排一个作业 ──► 停下
 *                           ├─ 排他网关：接着走；并行、包容的分叉：选出的每条线都走；汇合：等齐了才接着走
 *                           └─ 结束节点：finish（取消还开着的待办）──► 停下
 *     ▼
 *   flush() ──► 重算标题、摘要列、正在处理的节点；写回实例；写入这一步的流转记录
 * </pre>
 * 一路上记下这次新生成的待办（created）、结束的待办（closed）和流转记录（events），处理类把它们作为命令的结果返回。
 * 还没移植到 TS 的节点（等待外部事件、调用流程）遇到时报"还不支持"。
 */
import {
  applyEffects,
  FLOW_EVENT,
  FlowError,
  selectEdges,
  type CcNode,
  type FlowEvent,
  type GatewayPairs,
  type Id,
  type ProcessGraph,
  type ProcessNode,
  type ServiceNode,
  type TaskNode,
  type TimerNode,
  type WorkItem,
} from '@zerodep-dev/flow-model';
import { actorName, type Actor } from '../identity.js';
import { RunState } from '../run-state.js';
import { instanceView, type InstanceRecord, type JsonRecord, type NewCc } from '../store/types.js';
import { isAfter, millisOf, type Instant } from '../time.js';
import { AssigneeResolver, Assignees } from './assignees.js';
import type { EngineContext } from './context.js';
import { Deadlines } from './deadlines.js';
import type { LoadedDefinition } from './definitions.js';
import { refreshDisplay } from './display.js';
import { invalid } from './errors.js';
import { JOB, MAX_ATTEMPTS } from './jobs.js';
import { plainJson, readReference, resolveArgs } from './values.js';

/** 一次推进最多经过多少个节点：网关绕成圈、条件永远成立时停下来报错，而不是一直走 */
const MAX_STEPS = 1000;

/**
 * 待办从哪来：转办、委派、加签的人与原来的待办。流转生成的待办是 NONE。
 *
 * @param parentItemId 委派、加签时原来的待办；转办时为 null
 * @param addMode      delegate、before、after、parallel；转办时为 null
 */
export interface Origin {
  readonly fromUserId: Id | null;
  readonly fromUserName: string | null;
  readonly parentItemId: Id | null;
  readonly addMode: WorkItem['addMode'];
}

const NO_ORIGIN: Origin = { fromUserId: null, fromUserName: null, parentItemId: null, addMode: null };

/** 一个实例的推进 */
export class Execution {
  readonly graph: ProcessGraph;
  readonly deadlines: Deadlines;
  readonly #pairs: GatewayPairs;
  readonly #assignees: AssigneeResolver;
  #steps = 0;
  /** 这一步的处理人选的下一步处理人：节点 → 人 */
  #picks: ReadonlyMap<string, readonly Id[]> = new Map();
  /** 这次处理退回到哪个节点（处理结果 target: choose 时） */
  #returnTarget: string | null = null;
  /** 还没写库的流转记录 */
  readonly #pending: Omit<FlowEvent, 'id'>[] = [];

  /** 这次新生成的待办 */
  readonly created: WorkItem[] = [];
  /** 这次结束的待办（处理完的、取消的），是结束前的样子 */
  readonly closed: WorkItem[] = [];
  /** 这次写下的流转记录（带数据库给的编号） */
  readonly events: FlowEvent[] = [];

  /**
   * @param actor      这次的操作人；记进流转记录
   * @param definition 实例按的那个版本
   * @param rev        实例锁住后的版本：这一步的流转记录都带它
   */
  constructor(
    readonly context: EngineContext,
    readonly actor: Actor,
    readonly now: Instant,
    readonly definition: LoadedDefinition,
    public instance: InstanceRecord,
    public form: JsonRecord,
    readonly rev: number,
  ) {
    this.graph = definition.graph;
    this.#pairs = definition.pairs;
    this.#assignees = new AssigneeResolver(context.stores.workItems, context.org, context.extensions);
    this.deadlines = new Deadlines(context.calendar, definition.model.settings?.calendar ?? null);
  }

  /** 这一步的处理人选的下一步处理人 */
  set picks(next: ReadonlyMap<string, readonly Id[]>) {
    this.#picks = next;
  }

  /** 退回时处理人选的节点（处理结果 target: choose，已经核对过是能选的） */
  set returnTarget(node: string) {
    this.#returnTarget = node;
  }

  /** 按引用的名字取值：用推进到此刻的表单与实例 */
  readonly read = (name: string): unknown => readReference(name, this.instance, this.form);

  #state(change: (state: RunState) => RunState) {
    this.instance = { ...this.instance, state: change(this.instance.state) };
  }

  // ───────── 推进 ─────────

  /** 离开一个节点：按结果与条件选出线，每条线应用副作用、进入下一个节点；并行、包容的分叉先记下汇合要等几条分支 */
  async leave(nodeId: string, outcome: string | null): Promise<void> {
    const edges = selectEdges(this.graph, nodeId, { outcome, read: this.read });
    if (edges.length === 0) {
      // 发布时的校验保证每个结果都有出边；走到这里是条件都不成立、又没有默认边
      throw new FlowError('flow.definition-invalid', {
        message: `no edge leaves ${nodeId} for outcome ${outcome}`,
        errors: [{ code: 'no-edge-selected', node: nodeId }],
      });
    }
    const join = this.#pairs.joinOf(nodeId);
    if (join !== undefined) this.#state((s) => s.fork(join, edges.length));
    else if (edges.length > 1) throw new Error(`node ${nodeId} leaves on several edges but has no paired join`);
    // 从开始节点出来的待办是"发起"来的，其余是"流转"来的
    const arrival = this.graph.start?.id === nodeId ? 'start' : 'forward';
    for (const edge of edges) {
      if (this.instance.status !== 'active') return; // 前一条分支走到了终止的结束节点
      this.addEvent(FLOW_EVENT.NODE_LEFT, this.graph.node(nodeId), { edge: edge.id, outcome });
      if (edge.effects && edge.effects.length > 0) {
        this.instance = { ...this.instance, variables: applyEffects(edge.effects, this.instance.variables, this.read) };
      }
      const backward = edge.kind === 'return' || edge.kind === 'escalate';
      if (backward) {
        await this.#leaveRegions(nodeId, edge.to);
        this.#rememberResubmit(nodeId, edge.to, edge.kind!);
      }
      await this.#enter(edge.to, backward ? edge.kind! : arrival);
    }
  }

  /** 处理完一个节点后往下走：被退回来的、退回它的节点要求"重新提交后直接回来"时直接回去；否则照常 leave */
  async proceed(nodeId: string, outcome: string | null): Promise<void> {
    const back = this.instance.state.resubmitTo(nodeId);
    if (back === null) return this.leave(nodeId, outcome);
    this.#state((s) => s.resubmitted(nodeId));
    this.addEvent(FLOW_EVENT.NODE_LEFT, this.graph.node(nodeId), { outcome });
    await this.#enter(back, 'resubmit');
  }

  /** 退回、提级的线跳出了并行区域：区域里其余还开着的待办一起取消，汇合也不再等它们；由里到外看每一层区域 */
  async #leaveRegions(from: string, to: string) {
    for (const fork of this.#pairs.forksAround(from)) {
      const region = this.#pairs.region(fork);
      if (region.has(to)) return;
      for (const item of await this.context.stores.workItems.cancelOpenAt(this.instance.id, [...region], this.now)) {
        this.closed.push(item);
        this.addEvent(FLOW_EVENT.WORK_ITEM_CANCELED, this.graph.node(item.node), { workItemId: item.id });
      }
      // 区域里停在定时节点上的分支也不再等
      for (const node of region) this.#state((s) => s.waitDone(node));
      this.#state((s) => s.forget(this.#pairs.joinOf(fork)));
    }
  }

  /** 退回它的节点写了 returnTo: "resubmit"：记下来，被退回的节点重新提交后直接回来 */
  #rememberResubmit(from: string, to: string, kind: string) {
    const node = this.graph.node(from);
    if (kind === 'return' && node?.type === 'task' && node.returnTo === 'resubmit') this.#state((s) => s.returned(to, from));
  }

  /** 退回到处理人选的节点（target: choose）：同沿退回的线走；node.left 的 data 里记着目标 */
  async returnTo(from: string, outcome: string): Promise<void> {
    const target = this.#returnTarget;
    if (target === null) throw invalid('returnTarget', 'required');
    this.addEvent(FLOW_EVENT.NODE_LEFT, this.graph.node(from), { outcome, data: { target } });
    await this.#leaveRegions(from, target);
    this.#rememberResubmit(from, target, 'return');
    await this.#enter(target, 'return');
  }

  /** 进入一个节点，按节点类型做它该做的事 */
  async #enter(nodeId: string, arrival: string): Promise<void> {
    if (++this.#steps > MAX_STEPS) throw new Error(`the instance passed ${MAX_STEPS} nodes in one step; the gateways loop`);
    const node = this.graph.node(nodeId)!;
    this.addEvent(FLOW_EVENT.NODE_ENTERED, node);
    switch (node.type) {
      case 'task':
        return this.#arriveAtTask(node, arrival);
      case 'exclusive':
      case 'parallel':
      case 'inclusive':
        return this.#arriveAtGateway(nodeId);
      case 'timer':
        return this.#arriveAtTimer(node);
      case 'cc':
        return this.#arriveAtCc(node);
      case 'service':
        return this.#arriveAtService(node);
      case 'end':
        return this.finish('completed', node.result ?? null, FLOW_EVENT.INSTANCE_FINISHED, null);
      default:
        throw new Error(`node type '${node.type}' (${nodeId}) is not supported by the TypeScript engine yet`);
    }
  }

  /** 到达网关：排他网关与分叉直接往下走；汇合到了一条就少等一条，等齐了才往下走 */
  async #arriveAtGateway(nodeId: string) {
    if (this.#pairs.forkOf(nodeId) !== undefined) {
      const last = this.instance.state.waitingAt(nodeId) <= 1;
      this.#state((s) => s.arrive(nodeId));
      if (!last) return;
    }
    await this.leave(nodeId, null);
  }

  /** 到达定时节点：轮次加一，算出到什么时候，记在运行状态里、排一个作业；时刻已经过了、引用没有值时直接往下走 */
  async #arriveAtTimer(timer: TimerNode) {
    this.#state((s) => s.enter(timer.id));
    const until = this.deadlines.of(timer.wait, this.now, this.read);
    if (until === null || !isAfter(until, this.now)) return this.leave(timer.id, null);
    this.#state((s) => s.waitUntil(timer.id, until));
    await this.#schedule(JOB.TIMER, null, until, { node: timer.id, round: this.instance.state.round(timer.id) }, MAX_ATTEMPTS);
  }

  /** 定时节点到时候了：不再等，往下走 */
  async timerFired(nodeId: string): Promise<void> {
    this.#state((s) => s.waitDone(nodeId));
    await this.leave(nodeId, null);
  }

  /** 到达自动节点：同步的马上做动作、按结果往下走；异步的（默认）停在这里、排一个 action 作业 */
  async #arriveAtService(service: ServiceNode) {
    this.#state((s) => s.enter(service.id));
    if (service.mode === 'sync') return this.performAction(service);
    this.#state((s) => s.waitUntil(service.id, this.now));
    const payload: Record<string, unknown> = { node: service.id, round: this.instance.state.round(service.id) };
    if (service.retry?.backoff) {
      payload['backoffSeconds'] = Math.round((millisOf(this.deadlines.plus(service.retry.backoff, this.now)) - millisOf(this.now)) / 1000);
    }
    // retry.times 是失败后再试几次：一共做 times + 1 次
    const attempts = service.retry === undefined ? MAX_ATTEMPTS : service.retry.times + 1;
    await this.#schedule(JOB.ACTION, null, this.now, payload, attempts);
  }

  /** 做自动节点的动作，改它给的流程变量，按它给的结果往下走；动作抛的不是业务错误的包成 `flow.extension-failed` */
  async performAction(service: ServiceNode): Promise<void> {
    const key = `${this.instance.id}:${service.id}:${this.instance.state.round(service.id)}`;
    let result;
    try {
      result = await this.context.extensions.action(service.action).perform({
        instance: instanceView(this.instance),
        node: service.id,
        args: resolveArgs(service.args, this.read),
        idempotencyKey: key,
        read: this.read,
      });
    } catch (error) {
      if (error instanceof FlowError) throw error;
      throw new FlowError('flow.extension-failed', {
        message: `action ${service.action} failed at ${service.id}`,
        params: { kind: 'action', name: service.action },
        cause: error,
      });
    }
    if (result.variables && Object.keys(result.variables).length > 0) {
      this.instance = { ...this.instance, variables: { ...this.instance.variables, ...result.variables } };
    }
    this.#state((s) => s.waitDone(service.id));
    await this.leave(service.id, result.outcome ?? null);
  }

  /** 到达抄送节点：抄送给节点上写的人（角色、部门展开成人），不停，接着往下走 */
  async #arriveAtCc(cc: CcNode) {
    const users = (await this.#assignees.people(cc.to, this.instance, this.read)).map((p) => p.user);
    await this.copyTo(cc, users, cc.message ?? null);
    await this.leave(cc.id, null);
  }

  /** 抄送给这些人（同一个人只一条）；node 为 null 时是手动抄送（抄送人记在抄送上） */
  async copyTo(node: CcNode | null, users: readonly Id[], message: string | null): Promise<void> {
    const recipients = [...new Set(users)];
    if (recipients.length === 0) return;
    const names = recipients.map((id) => this.context.org.userName(id));
    const rows: NewCc[] = recipients.map((recipientId, i) => ({
      instanceId: this.instance.id,
      node: node?.id ?? null,
      nodeName: node?.name ?? null,
      recipientId,
      recipientName: names[i]!,
      fromUserId: node ? null : this.actor.id,
      fromUserName: node ? null : actorName(this.actor),
      message,
      createdAt: this.now,
    }));
    await this.context.stores.ccs.insert(rows);
    this.addEvent(FLOW_EVENT.INSTANCE_CC, node, { comment: message, data: { users: recipients, userNames: names } });
  }

  /** 到达人工节点：轮次加一，解析处理人，生成一个待办（会签时每人一份） */
  async #arriveAtTask(task: TaskNode, arrival: string) {
    this.#state((s) => s.enter(task.id));
    if (task.multi) return this.#arriveAtCountersign(task, arrival);
    const who = await this.#assigneesFor(task, arrival);
    if (who.isEmpty) return this.#handleEmptyAssignee(task);
    await this.#createWorkItem(task, arrival, who, null, null);
  }

  /** 到达会签节点：每个参与人一份待办；依次会签只给第一个人，其余排队 */
  async #arriveAtCountersign(task: TaskNode, arrival: string) {
    const participants = await this.#assignees.participants(task, this.instance, this.read, this.#picks);
    if (participants.length === 0) return this.#handleEmptyAssignee(task);
    const sequential = task.multi!.complete === 'sequential';
    const weighted = task.multi!.complete === 'votes';
    const first = sequential ? participants.slice(0, 1) : participants;
    for (let i = 0; i < first.length; i++) {
      await this.#createWorkItem(task, arrival, Assignees.of(first[i]!.user), i + 1, weighted ? first[i]!.weight : null);
    }
    if (sequential) this.#state((s) => s.withQueue(task.id, participants.slice(1).map((p) => p.user)));
  }

  /** 依次会签：轮到排队的下一个人（前一个人同意了） */
  async nextSigner(task: TaskNode, seq: number): Promise<void> {
    const queue = this.instance.state.queue(task.id);
    this.#state((s) => s.withQueue(task.id, queue.slice(1)));
    await this.#createWorkItem(task, 'forward', Assignees.of(queue[0]!), seq, null);
  }

  /** 会签节点结束了：其余还开着的待办取消，排队的人也不用再等 */
  async closeCountersign(task: TaskNode): Promise<void> {
    this.#state((s) => s.withQueue(task.id, []));
    for (const item of await this.context.stores.workItems.cancelOpenAt(this.instance.id, [task.id], this.now)) {
      this.closed.push(item);
      this.addEvent(FLOW_EVENT.WORK_ITEM_CANCELED, task, { workItemId: item.id });
    }
  }

  /** 生成一个待办：流转来的按节点的时限算截止时间 */
  #createWorkItem(task: TaskNode, arrival: string, who: Assignees, signSeq: number | null, weight: number | null) {
    const due = task.due ? this.deadlines.of(task.due, this.now, this.read) : null;
    return this.createWorkItem(task, arrival, who, signSeq, weight, NO_ORIGIN, 'open', due);
  }

  /**
   * 生成一个待办：只有一个人、没有收件箱时直接落到他；否则进收件箱（pool），人和角色、部门都存成候选，谁先认领谁办。
   *
   * @param state open，或后加签时先生成、原来的人处理完才打开的 waiting
   * @param dueAt 截止时间：流转来的按节点的时限算，转办、委派、加签的沿用原来那一份的
   */
  async createWorkItem(
    task: TaskNode,
    arrival: string,
    who: Assignees,
    signSeq: number | null,
    weight: number | null,
    origin: Origin,
    state: WorkItem['state'],
    dueAt: Instant | null,
  ): Promise<WorkItem> {
    const assigneeId = who.isDirect ? who.directUser : null;
    const assigneeName = assigneeId === null ? null : this.context.org.userName(assigneeId);
    const inbox = who.firstInbox;
    const draft: Omit<WorkItem, 'id'> = {
      instanceId: this.instance.id,
      processKey: this.instance.processKey,
      priority: this.instance.priority,
      node: task.id,
      nodeName: task.name,
      round: this.instance.state.round(task.id),
      signSeq,
      weight,
      state,
      assignment: assigneeId !== null ? 'direct' : 'pool',
      arrival,
      inboxKind: inbox?.kind ?? null,
      inboxId: inbox?.targetId ?? null,
      assigneeId,
      assigneeName,
      fromUserId: origin.fromUserId,
      fromUserName: origin.fromUserName,
      parentItemId: origin.parentItemId,
      addMode: origin.addMode,
      createdAt: this.now,
      claimedAt: null,
      dueAt,
      outcome: null,
      comment: null,
      completedBy: null,
      completedByName: null,
      completedAt: null,
    };
    const item = await this.context.stores.workItems.insert(draft, assigneeId !== null ? [] : who.candidates());
    this.created.push(item);
    await this.#scheduleTimeouts(task, item);
    // 直接落到人的记下名字快照：流转记录上显示"到达 某节点 · 处理人 张三"；在收件箱里的没有
    this.addEvent(FLOW_EVENT.WORK_ITEM_CREATED, task, { workItemId: item.id, data: assigneeName === null ? {} : { assigneeName } });
    return item;
  }

  /** 把一个待办交给别人（转办、委派、加签）：直接落到这个人，会签的序号与权重照抄原来的那一份 */
  handOver(from: WorkItem, userId: Id, arrival: string, addMode: WorkItem['addMode'], state: WorkItem['state']): Promise<WorkItem> {
    const task = this.graph.node(from.node) as TaskNode;
    const origin: Origin = { fromUserId: this.actor.id, fromUserName: actorName(this.actor), parentItemId: addMode === null ? null : from.id, addMode };
    return this.createWorkItem(task, arrival, Assignees.of(userId), from.signSeq, from.weight, origin, state, from.dueAt);
  }

  /** 待办的超时、提醒、无人认领各排一个作业（待办先办完了也不用删：作业到时候看到待办不再打开就跳过） */
  async #scheduleTimeouts(task: TaskNode, item: WorkItem) {
    if (item.dueAt !== null) {
      await this.#schedule(JOB.OVERDUE, item.id, item.dueAt, {}, MAX_ATTEMPTS);
      const reminders = task.reminders ?? [];
      for (let i = 0; i < reminders.length; i++) {
        const reminder = reminders[i]!;
        const at = 'before' in reminder ? this.deadlines.minus(reminder.before, item.dueAt) : this.deadlines.plus(reminder.every, item.dueAt);
        if (isAfter(at, this.now)) await this.#schedule(JOB.REMIND, item.id, at, { reminder: i, sent: 0 }, MAX_ATTEMPTS);
      }
    }
    if (task.unclaimed && item.assigneeId === null) {
      await this.#schedule(JOB.UNCLAIMED, item.id, this.deadlines.plus(task.unclaimed.after, this.now), {}, MAX_ATTEMPTS);
    }
  }

  #schedule(kind: string, workItemId: Id | null, dueAt: Instant, payload: JsonRecord, maxAttempts: number) {
    return this.context.stores.jobs.schedule({ kind, instanceId: this.instance.id, workItemId, dueAt, payload, maxAttempts, createdAt: this.now });
  }

  /** 这次到达人工节点由谁处理：退回来的、重新提交直接回来的落到原来的处理人；提级来的只进收件箱；其余照节点上写的来源解析 */
  async #assigneesFor(task: TaskNode, arrival: string): Promise<Assignees> {
    if (arrival === 'return' || arrival === 'resubmit') {
      const last = await this.context.stores.workItems.lastCompletedBy(this.instance.id, task.id);
      if (last !== null) return Assignees.of(last);
    }
    const who = await this.#assignees.resolve(task, this.graph, this.instance, this.read, this.#picks);
    return arrival === 'escalate' && who.inboxes.length > 0 ? who.inboxesOnly() : who;
  }

  /** 处理人解析为空：autoPass 按第一个"同意"的结果自动通过；admin 转给流程管理员；其余（error，默认）报 assignee-empty */
  async #handleEmptyAssignee(task: TaskNode) {
    const rule = task.emptyAssignee ?? this.graph.definition.settings?.emptyAssignee;
    if (rule === 'autoPass') {
      const outcome = task.outcomes.find((o) => (o.kind ?? 'approve') === 'approve')?.key ?? null;
      return this.leave(task.id, outcome);
    }
    const admins = rule === 'admin' ? (this.context.org.administrators?.() ?? []) : [];
    if (admins.length > 0) {
      const who = new Assignees();
      for (const admin of admins) who.users.add(admin);
      await this.#createWorkItem(task, 'emptyAssignee', who, null, null);
      return;
    }
    throw new FlowError('flow.validation-failed', { message: `nobody can handle node ${task.id}`, errors: [{ code: 'assignee-empty', node: task.id }] });
  }

  /** 撤回刚处理的一步：运行状态、流程变量换回处理之前的 */
  restore(state: RunState, variables: JsonRecord): void {
    this.instance = { ...this.instance, state, variables };
  }

  /** 换实例的状态（暂停、恢复），记一条流转记录 */
  changeStatus(status: InstanceRecord['status'], eventType: string, comment: string | null): void {
    this.instance = { ...this.instance, status };
    this.addEvent(eventType, null, { comment });
  }

  /** 管理员跳转：还没办完的待办都取消，等待中的汇合、队列都不再记，从这个节点重新走 */
  async jumpTo(nodeId: string, reason: string | null): Promise<void> {
    for (const item of await this.context.stores.workItems.cancelOpen(this.instance.id, this.now)) {
      this.closed.push(item);
      this.addEvent(FLOW_EVENT.WORK_ITEM_CANCELED, this.graph.node(item.node), { workItemId: item.id });
    }
    this.#state((s) => s.withoutPending());
    this.addEvent(FLOW_EVENT.INSTANCE_JUMPED, this.graph.node(nodeId), { comment: reason });
    await this.#enter(nodeId, 'jump');
  }

  /** 结束实例：取消还开着的待办，记结束的流转记录 */
  async finish(status: InstanceRecord['status'], result: string | null, eventType: string, comment: string | null): Promise<void> {
    for (const item of await this.context.stores.workItems.cancelOpen(this.instance.id, this.now)) {
      this.closed.push(item);
      this.addEvent(FLOW_EVENT.WORK_ITEM_CANCELED, this.graph.node(item.node), { workItemId: item.id });
    }
    this.instance = { ...this.instance, status, result, endedAt: this.now, current: [] };
    await this.context.stores.jobs.cancelForInstance(this.instance.id);
    await this.context.stores.steps.remove(this.instance.id);
    this.addEvent(eventType, null, { outcome: result, comment });
  }

  /** 记下一条流转记录（先放着，flush 时一起写库）；同一步的记录共用实例的版本 */
  addEvent(
    type: string,
    node: ProcessNode | null | undefined,
    extra: { edge?: string | null; outcome?: string | null; workItemId?: Id | null; comment?: string | null; data?: JsonRecord } = {},
  ): void {
    this.#pending.push({
      instanceId: this.instance.id,
      workItemId: extra.workItemId ?? null,
      type,
      node: node?.id ?? null,
      nodeName: node?.name ?? null,
      edge: extra.edge ?? null,
      outcome: extra.outcome ?? null,
      instanceRev: this.rev,
      actorId: this.actor.id,
      actorName: actorName(this.actor),
      at: this.now,
      comment: extra.comment ?? null,
      // 附加数据是用户的 JSON：编号写成数（同存进库再读出来的样子，这一步返回的与以后查到的一致）
      data: plainJson(extra.data ?? {}) as JsonRecord,
    });
  }

  // ───────── 写回 ─────────

  /** 结束这一步：重算标题、摘要列、正在处理的节点，写回实例，写入这一步的流转记录 */
  async flush(fixedTitle: string | null): Promise<void> {
    this.instance = await refreshDisplay(this.instance, this.graph.definition, fixedTitle, this.read, this.context.stores.workItems);
    await this.context.stores.instances.update(this.instance);
    await this.publish();
  }

  /** 只写流转记录（不改实例的命令：认领、催办、留言、抄送）：换上数据库给的编号，交给监听 */
  async publish(): Promise<void> {
    const saved = await this.context.stores.events.append(this.#pending.splice(0));
    this.events.push(...saved);
    await this.context.dispatcher.published(saved, instanceView(this.instance), this.graph);
  }
}
