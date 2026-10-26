/**
 * 人工节点由谁处理（0005 的 3.2，同 Java 的 AssigneeResolver、Assignees、CandidateRules）：把节点上写的几个来源解析成人和收件箱，
 * 几个来源取并集。只有一个人、没有收件箱时直接落到这个人；否则进收件箱，人和收件箱都作为候选，谁先认领谁办。
 * <pre>
 *   { "user": 1001 } / { ref }     人（people 类型的值里有几个人就是几个）
 *   { "role": 11 }                收件箱：有这个角色的人认领
 *   { "department": 20 }          收件箱：这个部门的人认领
 *   { "initiator": true }         人：发起人
 *   { "sameAs": "check" }         人：那个节点最后的处理人
 *   { "resolver": "deptLeader" }  人：接入方注册的解析器算出来的
 *   { "group": "post", "id": 5 }  收件箱：接入方扩展的种类，候选按 Actor.groups 匹配
 *   { "pick": { "from": […] } }   人：上一步的处理人提交时选的（命令的 picks）
 *   （节点没写来源）              所在泳道的收件箱
 * </pre>
 */
import { isRef, type AssigneeSource, type Id, type ProcessGraph, type TaskNode } from '@zerodep-dev/flow-model';
import type { Extensions } from '../extensions.js';
import { actorGroups, type Actor, type OrgDirectory } from '../identity.js';
import { CANDIDATE, instanceView, type Candidate, type InstanceRecord, type WorkItemStore } from '../store/types.js';
import { invalid } from './errors.js';
import { orgId, orgIds, resolveArgs } from './values.js';

/** 解析出来的处理人：人（保持先后、去重）与收件箱 */
export class Assignees {
  readonly users = new Set<Id>();
  readonly inboxes: Candidate[] = [];

  /** 只有这一个人 */
  static of(user: Id): Assignees {
    const one = new Assignees();
    one.users.add(user);
    return one;
  }

  /** 只留收件箱（提级：进收件箱，不直接落到人） */
  inboxesOnly(): Assignees {
    const out = new Assignees();
    out.inboxes.push(...this.inboxes);
    return out;
  }

  /** 一个也没有：按定义的 emptyAssignee 处理 */
  get isEmpty(): boolean {
    return this.users.size === 0 && this.inboxes.length === 0;
  }

  /** 直接落到一个人：只有一个人，也没有收件箱 */
  get isDirect(): boolean {
    return this.users.size === 1 && this.inboxes.length === 0;
  }

  /** 直接落到的那个人 */
  get directUser(): Id {
    return [...this.users][0]!;
  }

  /** 第一个收件箱（待办上显示它属于哪个角色、部门）；没有时为 null */
  get firstInbox(): Candidate | null {
    return this.inboxes[0] ?? null;
  }

  /** 进收件箱时要存的候选：人在前，收件箱在后 */
  candidates(): Candidate[] {
    return [...[...this.users].map((user) => ({ kind: CANDIDATE.USER, targetId: user })), ...this.inboxes];
  }
}

/** 会签的一个参与人；weight 是票签的权重（写在指定的人上，其余是 1） */
export interface Participant {
  readonly user: Id;
  readonly weight: number;
}

/** 取引用的值 */
type Read = (name: string) => unknown;

/** 定义里写的值：直接写的值原样，引用去取 */
const valueOf = (literalOrRef: unknown, read: Read) => (isRef(literalOrRef) ? read(literalOrRef.ref) : literalOrRef);

/** 处理人的解析 */
export class AssigneeResolver {
  constructor(
    private readonly workItems: WorkItemStore,
    private readonly org: OrgDirectory,
    private readonly extensions: Extensions,
  ) {}

  /** 会签的参与人：来源列表里的人、角色与部门下的人，或引用的 people 值；同一个人只算一次（先出现的为准） */
  async participants(task: TaskNode, instance: InstanceRecord, read: Read, picks: ReadonlyMap<string, readonly Id[]>): Promise<Participant[]> {
    const out = new Map<Id, Participant>();
    const add = (p: Participant) => {
      if (!out.has(p.user)) out.set(p.user, p);
    };
    const participants = task.multi!.participants;
    if (isRef(participants)) {
      for (const id of orgIds(read(participants.ref))) add({ user: id, weight: 1 });
      return [...out.values()];
    }
    const others: AssigneeSource[] = [];
    for (const source of participants) {
      if ('pick' in source) for (const id of await this.#picked(task, source, picks, instance, read)) add({ user: id, weight: 1 });
      else others.push(source);
    }
    for (const p of await this.people(others, instance, read)) add(p);
    return [...out.values()];
  }

  /** 几个来源下的人：角色与部门下的人展开；同一个人只算一次（先出现的为准），保持先后。会签、抄送节点按它展开 */
  async people(sources: readonly AssigneeSource[], instance: InstanceRecord, read: Read): Promise<Participant[]> {
    const out = new Map<Id, Participant>();
    const add = (user: Id, weight = 1) => {
      if (!out.has(user)) out.set(user, { user, weight });
    };
    const members = (kind: string, id: Id) => {
      if (!this.org.members) throw new Error(`OrgDirectory.members is not implemented: countersign by ${kind} needs it`);
      for (const member of this.org.members(kind, id)) add(member);
    };
    for (const source of sources) {
      if ('user' in source) for (const id of orgIds(valueOf(source.user, read))) add(id, source.weight ?? 1);
      else if ('role' in source) for (const id of orgIds(valueOf(source.role, read))) members(CANDIDATE.ROLE, id);
      else if ('department' in source) for (const id of orgIds(valueOf(source.department, read))) members(CANDIDATE.DEPARTMENT, id);
      else if ('initiator' in source) {
        if (instance.startedBy != null) add(instance.startedBy);
      } else if ('sameAs' in source) {
        const last = await this.workItems.lastCompletedBy(instance.id, source.sameAs);
        if (last != null) add(last);
      } else if ('resolver' in source) for (const id of await this.#named(source, instance, read)) add(id);
      else if ('group' in source) for (const id of orgIds(valueOf(source.id, read))) members(source.group, id);
      else throw new Error(`assignee source ${JSON.stringify(source)} cannot be expanded into people yet`);
    }
    return [...out.values()];
  }

  /** 解析一个人工节点的处理人 */
  async resolve(task: TaskNode, graph: ProcessGraph, instance: InstanceRecord, read: Read, picks: ReadonlyMap<string, readonly Id[]>): Promise<Assignees> {
    const out = new Assignees();
    for (const source of sourcesOf(task, graph)) {
      if ('pick' in source) for (const id of await this.#picked(task, source, picks, instance, read)) out.users.add(id);
      else if ('user' in source) for (const id of orgIds(valueOf(source.user, read))) out.users.add(id);
      else if ('role' in source) for (const id of orgIds(valueOf(source.role, read))) out.inboxes.push({ kind: CANDIDATE.ROLE, targetId: id });
      else if ('department' in source) {
        for (const id of orgIds(valueOf(source.department, read))) out.inboxes.push({ kind: CANDIDATE.DEPARTMENT, targetId: id });
      } else if ('initiator' in source) {
        if (instance.startedBy != null) out.users.add(instance.startedBy);
      } else if ('sameAs' in source) {
        const last = await this.workItems.lastCompletedBy(instance.id, source.sameAs);
        if (last != null) out.users.add(last);
      } else if ('resolver' in source) for (const id of await this.#named(source, instance, read)) out.users.add(id);
      else if ('group' in source) for (const id of orgIds(valueOf(source.id, read))) out.inboxes.push({ kind: source.group, targetId: id });
      else throw new Error(`assignee source ${JSON.stringify(source)} is not supported yet`);
    }
    return out;
  }

  /** 上一步选的人：没选报 required，超过 max 报 too-many，不在 from 解析出的人里报 not-allowed（from 不写时谁都可以） */
  async #picked(
    task: TaskNode,
    source: Extract<AssigneeSource, { pick: unknown }>,
    picks: ReadonlyMap<string, readonly Id[]>,
    instance: InstanceRecord,
    read: Read,
  ): Promise<Id[]> {
    const field = `picks.${task.id}`;
    const chosen = [...new Set(picks.get(task.id) ?? [])];
    if (chosen.length === 0) throw invalid(field, 'required');
    if (source.pick.max != null && chosen.length > source.pick.max) throw invalid(field, 'too-many');
    const from = source.pick.from ?? [];
    if (from.length > 0) {
      const allowed = new Set((await this.people(from, instance, read)).map((p) => p.user));
      if (!chosen.every((id) => allowed.has(id))) throw invalid(field, 'not-allowed');
    }
    return chosen;
  }

  /** 接入方注册的解析器算出的人；参数里的引用先换成值 */
  async #named(source: Extract<AssigneeSource, { resolver: string }>, instance: InstanceRecord, read: Read): Promise<Id[]> {
    const resolver = this.extensions.resolver(source.resolver);
    return [...(await resolver.resolve({ instance: instanceView(instance), args: resolveArgs(source.args, read), read }))];
  }
}

/** 节点上写的来源；没写时用所在泳道的收件箱 */
function sourcesOf(task: TaskNode, graph: ProcessGraph): readonly AssigneeSource[] {
  if (task.assignees && task.assignees.length > 0) return task.assignees;
  const inbox = graph.lane(task.lane)?.inbox;
  if (!inbox) return [];
  if ('role' in inbox) return [{ role: inbox.role }];
  if ('department' in inbox) return [{ department: inbox.department }];
  return [{ group: inbox.group, id: inbox.id }];
}

/** 候选里有我、我的角色、部门或扩展的归属，而且我没被排除（退回过收件箱的人不能再认领） */
export function isCandidate(actor: Actor, candidates: readonly Candidate[]): boolean {
  if (candidates.some((c) => c.kind === CANDIDATE.EXCLUDED && c.targetId === actor.id)) return false;
  return candidates.some((c) => {
    switch (c.kind) {
      case CANDIDATE.USER:
        return c.targetId === actor.id;
      case CANDIDATE.ROLE:
        return actor.roles.has(c.targetId);
      case CANDIDATE.DEPARTMENT:
        return actor.departments.has(c.targetId);
      case CANDIDATE.EXCLUDED:
        return false;
      default:
        return actorGroups(actor, c.kind).has(c.targetId);
    }
  });
}

/** 能不能发起（starters）：名单里的角色、部门、指定的人、扩展的种类，命中一个就行；只认写死的值（发起时还取不到引用） */
export function isStarter(actor: Actor, starters: readonly AssigneeSource[] | undefined): boolean {
  if (!starters || starters.length === 0) return true;
  return starters.some((source) => {
    if ('user' in source) return !isRef(source.user) && orgId(source.user) === actor.id;
    if ('role' in source) return !isRef(source.role) && actor.roles.has(orgId(source.role)!);
    if ('department' in source) return !isRef(source.department) && actor.departments.has(orgId(source.department)!);
    if ('group' in source) return !isRef(source.id) && actorGroups(actor, source.group).has(orgId(source.id)!);
    return false;
  });
}
