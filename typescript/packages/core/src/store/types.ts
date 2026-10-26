/**
 * 引擎用到的存储（0006 第 6 节"存储"一行）：默认都来自 query 的实现（`store/query`），每个都能单独换。同 Java 的 store 包，
 * 方法都返回 Promise。状态的变化写成带条件的更新，返回"改到没有"，所以认领先到先得、重复提交挡得住。
 */
import type { CurrentNode, FlowEvent, Id, InstanceStatus, ProcessInstance, WorkItem } from '@zerodep-dev/flow-model';
import type { Page } from '@zerodep-dev/query-core';
import type { RunState } from '../run-state.js';
import type { Instant } from '../time.js';

/** JSON 形状的一组值：表单的值、流程变量、摘要列 */
export type JsonRecord = Readonly<Record<string, unknown>>;

// ───────── 实例 ─────────

/** 实例的一行。不可变：推进时用对象展开换出新的一份 */
export interface InstanceRecord {
  readonly id: Id;
  readonly processKey: string;
  /** 按哪个版本走 */
  readonly definitionId: Id;
  readonly version: number;
  readonly businessKey: string | null;
  readonly title: string | null;
  readonly priority: number;
  readonly summary: JsonRecord;
  readonly status: InstanceStatus;
  readonly result: string | null;
  readonly current: readonly CurrentNode[];
  readonly variables: JsonRecord;
  /** 运行状态，引擎内部用 */
  readonly state: RunState;
  readonly startedBy: Id | null;
  readonly startedByName: string | null;
  readonly startedAt: Instant;
  readonly endedAt: Instant | null;
  /** 调用它的父实例；不是子流程时为 null */
  readonly parentInstanceId: Id | null;
  readonly parentNode: string | null;
  readonly parentRound: number | null;
  /** 最外层的实例；不是子流程时为 null */
  readonly rootInstanceId: Id | null;
  readonly rev: number;
}

/** 给前端的形状 */
export function instanceView(r: InstanceRecord): ProcessInstance {
  return {
    id: r.id,
    processKey: r.processKey,
    version: r.version,
    businessKey: r.businessKey,
    title: r.title,
    priority: r.priority,
    summary: r.summary,
    status: r.status,
    result: r.result,
    current: r.current,
    startedBy: r.startedBy,
    startedByName: r.startedByName,
    startedAt: r.startedAt,
    endedAt: r.endedAt,
    parentInstanceId: r.parentInstanceId,
  };
}

/** 管理端搜实例的条件：不写的不限 */
export interface InstanceSearch {
  readonly status?: string | null;
  readonly processKey?: string | null;
  readonly keyword?: string | null;
  readonly startedBy?: Id | null;
}

/** 实例：推进前先 lock 把版本加一（各库都会锁住这一行，并发的推进在这里排队） */
export interface InstanceStore {
  /** 新建，返回带上数据库给的编号的记录 */
  insert(instance: Omit<InstanceRecord, 'id'>): Promise<InstanceRecord>;
  /** 版本加一并锁住这一行，返回新的版本；实例不存在时为 null */
  lock(instanceId: Id): Promise<number | null>;
  find(instanceId: Id): Promise<InstanceRecord | null>;
  findAll(instanceIds: readonly Id[]): Promise<InstanceRecord[]>;
  search(search: InstanceSearch, page: number, size: number): Promise<Page<InstanceRecord>>;
  startedBy(userId: Id, page: number, size: number): Promise<Page<InstanceRecord>>;
  children(parentInstanceId: Id, parentNode: string, parentRound: number): Promise<InstanceRecord[]>;
  activeChildren(parentInstanceId: Id): Promise<InstanceRecord[]>;
  /** 整行写回（调用前已 lock；版本列照记录里的写，不再加一） */
  update(instance: InstanceRecord): Promise<void>;
}

// ───────── 待办 ─────────

/** 一行候选：user、role、department、扩展的种类，或 excluded（退回过收件箱，不能再认领） */
export interface Candidate {
  readonly kind: string;
  readonly targetId: Id;
}

/** 候选的种类 */
export const CANDIDATE = { USER: 'user', ROLE: 'role', DEPARTMENT: 'department', EXCLUDED: 'excluded' } as const;

/** 认领时写进待办的几列 */
export interface Claim {
  readonly assigneeId: Id;
  readonly assigneeName: string;
  readonly claimedAt: Instant;
}

/** 处理时写进待办的几列 */
export interface Completion {
  readonly outcome: string | null;
  readonly comment: string | null;
  readonly completedBy: Id | null;
  readonly completedByName: string | null;
  readonly completedAt: Instant;
}

/** 拿来匹配候选的归属：候选种类 → 我在这一种里的编号 */
export interface Member {
  readonly userId: Id;
  readonly memberships: ReadonlyMap<string, ReadonlySet<Id>>;
}

/** 待办与候选：候选只在待办打开时存在 */
export interface WorkItemStore {
  insert(item: Omit<WorkItem, 'id'>, candidates: readonly Candidate[]): Promise<WorkItem>;
  find(workItemId: Id): Promise<WorkItem | null>;
  candidates(workItemId: Id): Promise<Candidate[]>;
  claim(workItemId: Id, claim: Claim): Promise<boolean>;
  complete(workItemId: Id, completion: Completion): Promise<boolean>;
  /** 取消实例上全部还没办完的待办，返回取消之前的样子 */
  cancelOpen(instanceId: Id, at: Instant): Promise<WorkItem[]>;
  /** 只取消这些节点上还没办完的 */
  cancelOpenAt(instanceId: Id, nodes: readonly string[], at: Instant): Promise<WorkItem[]>;
  cancel(workItemId: Id, at: Instant): Promise<boolean>;
  changeState(workItemId: Id, from: string, to: string): Promise<boolean>;
  release(workItemId: Id, userId: Id): Promise<boolean>;
  reassign(workItemId: Id, userId: Id, userName: string): Promise<boolean>;
  remind(workItemId: Id, at: Instant, notBefore: Instant): Promise<boolean>;
  reopen(workItemId: Id): Promise<boolean>;
  needDesignation(workItemId: Id, designatorId: Id | null): Promise<boolean>;
  awaitsDesignation(workItemId: Id, designatorId: Id): Promise<boolean>;
  awaitingDesignation(designatorId: Id, page: number, size: number): Promise<Page<WorkItem>>;
  designate(workItemId: Id, designatorId: Id, userId: Id, userName: string): Promise<boolean>;
  children(parentItemId: Id): Promise<WorkItem[]>;
  /** 实例的全部待办，按到达的先后 */
  ofInstance(instanceId: Id): Promise<WorkItem[]>;
  /** 某个节点最后一次是谁处理的；没处理过时为 null */
  lastCompletedBy(instanceId: Id, node: string): Promise<Id | null>;
  todo(member: Member, page: number, size: number): Promise<Page<WorkItem>>;
  done(userId: Id, page: number, size: number): Promise<Page<WorkItem>>;
}

// ───────── 流程定义与表单 ─────────

/** 流程本身：启用停用与最新版本 */
export interface StoredProcess {
  readonly processKey: string;
  readonly name: string;
  readonly category: string | null;
  readonly status: string;
  readonly latestVersion: number;
}

/** 流程、表单的状态 */
export const STATUS = { ACTIVE: 'active', DISABLED: 'disabled' } as const;

/** 发布的一个版本；formKey、formVersion 是发布时定下的表单版本 */
export interface StoredDefinition {
  readonly id: Id;
  readonly processKey: string;
  readonly version: number;
  /** 0005 的整份定义的 JSON 文字 */
  readonly model: string;
  readonly formKey: string | null;
  readonly formVersion: number | null;
}

/** 版本列表的一行 */
export interface VersionInfo {
  readonly version: number;
  readonly note: string | null;
  readonly publishedByName: string | null;
  readonly publishedAt: Instant;
  readonly formKey: string | null;
  readonly formVersion: number | null;
}

/** 发布一个版本要的东西 */
export interface DefinitionPublication {
  readonly processKey: string;
  readonly name: string;
  readonly category: string | null;
  readonly model: string;
  readonly formKey: string | null;
  readonly formVersion: number | null;
  readonly note: string | null;
  readonly publishedBy: Id | null;
  readonly publishedByName: string | null;
  readonly at: Instant;
}

/** 流程本身与发布的版本 */
export interface DefinitionStore {
  publish(publication: DefinitionPublication): Promise<StoredDefinition>;
  process(processKey: string): Promise<StoredProcess | null>;
  processes(): Promise<StoredProcess[]>;
  latest(processKey: string): Promise<StoredDefinition | null>;
  byId(definitionId: Id): Promise<StoredDefinition | null>;
  version(processKey: string, version: number): Promise<StoredDefinition | null>;
  versions(processKey: string): Promise<VersionInfo[]>;
  setStatus(processKey: string, status: string): Promise<boolean>;
}

/** 表单发布的一个版本：引擎用到的是字段清单 */
export interface StoredForm {
  readonly id: Id;
  readonly formKey: string;
  readonly version: number;
  /** 字段清单的 JSON 文字 */
  readonly manifest: string;
}

/** 一个版本的前端部分 */
export interface FormClient {
  readonly clientSource: string | null;
  readonly clientCompiled: string | null;
  readonly compilerVersion: string | null;
}

/** 表单本身 */
export interface FormInfo {
  readonly formKey: string;
  readonly name: string;
  readonly category: string | null;
  readonly status: string;
  readonly latestVersion: number;
}

/** 表单版本列表的一行 */
export interface FormVersionInfo {
  readonly version: number;
  readonly note: string | null;
  readonly publishedByName: string | null;
  readonly publishedAt: Instant;
}

/** 一个版本的源码与字段清单 */
export interface FormSource {
  readonly formKey: string;
  readonly version: number;
  readonly manifest: string;
  readonly clientSource: string | null;
  readonly serverLanguage: string | null;
  readonly serverSource: string | null;
}

/** 发布一个表单版本要的东西 */
export interface FormPublicationRecord {
  readonly formKey: string;
  readonly name: string;
  readonly category: string | null;
  readonly manifest: string;
  readonly clientSource: string | null;
  readonly clientCompiled: string | null;
  readonly compilerVersion: string | null;
  readonly serverLanguage: string | null;
  readonly serverSource: string | null;
  readonly note: string | null;
  readonly publishedBy: Id | null;
  readonly publishedByName: string | null;
  readonly at: Instant;
}

/** 表单本身与发布的版本 */
export interface FormStore {
  publish(publication: FormPublicationRecord): Promise<StoredForm>;
  version(formKey: string, version: number): Promise<StoredForm | null>;
  latest(formKey: string): Promise<StoredForm | null>;
  byId(formVersionId: Id): Promise<StoredForm | null>;
  client(formVersionId: Id): Promise<FormClient | null>;
  forms(): Promise<FormInfo[]>;
  form(formKey: string): Promise<FormInfo | null>;
  versions(formKey: string): Promise<FormVersionInfo[]>;
  source(formKey: string, version: number): Promise<FormSource | null>;
  setStatus(formKey: string, status: string): Promise<boolean>;
}

/** 存着的一份表单值 */
export interface StoredFormData {
  readonly formVersionId: Id;
  readonly values: JsonRecord;
}

/** 实例上表单的值，每个实例一行 */
export interface FormDataStore {
  read(instanceId: Id): Promise<StoredFormData | null>;
  insert(instanceId: Id, formVersionId: Id, values: JsonRecord): Promise<void>;
  update(instanceId: Id, values: JsonRecord): Promise<void>;
}

// ───────── 流转记录、作业、抄送、快照 ─────────

/** 流转记录，只追加 */
export interface EventStore {
  /** 追加几条，返回带上数据库给的编号的记录（先后不变） */
  append(events: readonly Omit<FlowEvent, 'id'>[]): Promise<FlowEvent[]>;
  timeline(instanceId: Id): Promise<FlowEvent[]>;
}

/** 一个作业 */
export interface Job {
  readonly id: Id;
  readonly kind: string;
  readonly instanceId: Id;
  readonly workItemId: Id | null;
  readonly dueAt: Instant;
  readonly payload: JsonRecord;
  readonly state: string;
  readonly attempts: number;
  readonly maxAttempts: number;
  readonly lockedBy: string | null;
  readonly lockedUntil: Instant | null;
  readonly lastError: string | null;
  readonly createdAt: Instant;
}

/** 作业的状态 */
export const JOB_STATE = { PENDING: 'pending', FAILED: 'failed' } as const;

/** 要建的作业（租户、编号、状态由存储填） */
export interface NewJob {
  readonly kind: string;
  readonly instanceId: Id;
  readonly workItemId: Id | null;
  readonly dueAt: Instant;
  readonly payload: JsonRecord;
  readonly maxAttempts: number;
  readonly createdAt: Instant;
}

/** 作业：到时候要做的事 */
export interface JobStore {
  schedule(job: NewJob): Promise<void>;
  /** 到期、没人领着（或领的人超时了）的作业，最多 limit 个；跨租户 */
  due(now: Instant, limit: number): Promise<Job[]>;
  lock(jobId: Id, owner: string, now: Instant, until: Instant): Promise<boolean>;
  done(jobId: Id): Promise<void>;
  reschedule(jobId: Id, dueAt: Instant, payload: JsonRecord): Promise<void>;
  retry(jobId: Id, dueAt: Instant, error: string): Promise<void>;
  fail(jobId: Id, error: string): Promise<void>;
  revive(jobId: Id, now: Instant): Promise<boolean>;
  find(jobId: Id): Promise<Job | null>;
  failed(instanceId: Id | null, page: number, size: number): Promise<Page<Job>>;
  /** 实例结束了：它还没做的作业都删掉（失败的留着） */
  cancelForInstance(instanceId: Id): Promise<void>;
}

/** 要写的一条抄送 */
export interface NewCc {
  readonly instanceId: Id;
  readonly node: string | null;
  readonly nodeName: string | null;
  readonly recipientId: Id;
  readonly recipientName: string;
  readonly fromUserId: Id | null;
  readonly fromUserName: string | null;
  readonly message: string | null;
  readonly createdAt: Instant;
}

/** 抄送 */
export interface CcStore {
  insert(ccs: readonly NewCc[]): Promise<void>;
  received(recipientId: Id, page: number, size: number): Promise<Page<import('@zerodep-dev/flow-model').Cc>>;
  markRead(instanceId: Id, recipientId: Id, at: Instant): Promise<number>;
}

/** 最近一次处理之前的快照（撤回刚处理的一步用） */
export interface StepSnapshot {
  readonly instanceId: Id;
  readonly workItemId: Id;
  readonly rev: number;
  readonly state: RunState;
  readonly variables: JsonRecord;
  readonly form: JsonRecord;
}

/** 处理之前的快照：每个实例只留一份 */
export interface StepStore {
  save(snapshot: StepSnapshot): Promise<void>;
  find(instanceId: Id): Promise<StepSnapshot | null>;
  remove(instanceId: Id): Promise<void>;
}

/** 引擎用到的存储 */
export interface Stores {
  readonly definitions: DefinitionStore;
  readonly forms: FormStore;
  readonly formData: FormDataStore;
  readonly instances: InstanceStore;
  readonly workItems: WorkItemStore;
  readonly events: EventStore;
  readonly jobs: JobStore;
  readonly ccs: CcStore;
  readonly steps: StepStore;
}
