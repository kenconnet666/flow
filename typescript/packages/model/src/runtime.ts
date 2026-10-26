/**
 * 运行时的形状（0006 第 4 节）：命令与查询返回给前端的实例、待办、流转记录，两个后端相同（Java 的 model.runtime）。
 *
 * 编号是数据库自增的 BIGINT：线上是 typed JSON 的 bigint（文字加 `$types`），前端的请求层还原成 `bigint`；
 * 接入方组织表的编号（处理人、发起人）同样是 `bigint`。时间是带 `Z` 的 ISO 8601 文字。
 */

/** 数据库自增的编号 */
export type Id = bigint;

/** 实例的状态 */
export type InstanceStatus = 'active' | 'completed' | 'terminated' | 'withdrawn';

/** 正在处理的一个节点 */
export interface CurrentNode {
  readonly node: string;
  readonly name: string | null;
}

/** 流程实例 */
export interface ProcessInstance {
  readonly id: Id;
  /** 流程标识 */
  readonly processKey: string;
  /** 按哪个版本走 */
  readonly version: number;
  /** 业务单据编号 */
  readonly businessKey: string | null;
  /** 实例标题 */
  readonly title: string | null;
  /** 紧急程度，0 是常规，越大越急 */
  readonly priority: number;
  /** 待办列表的摘要列：`{ ref }` 的名字 → 值 */
  readonly summary: Readonly<Record<string, unknown>>;
  readonly status: InstanceStatus;
  /** 结束结果 */
  readonly result: string | null;
  /** 正在处理的节点 */
  readonly current: readonly CurrentNode[];
  /** 发起人 */
  readonly startedBy: Id | null;
  /** 发起人的名字（快照） */
  readonly startedByName: string | null;
  readonly startedAt: string;
  readonly endedAt: string | null;
}

/** 待办的状态 */
export type WorkItemState = 'open' | 'done' | 'canceled';

/** 落到谁：收件箱里等认领、认领了、直接落到人 */
export type WorkItemAssignment = 'pool' | 'claimed' | 'direct';

/** 一个待办 */
export interface WorkItem {
  readonly id: Id;
  readonly instanceId: Id;
  readonly processKey: string;
  /** 紧急程度（从实例抄来） */
  readonly priority: number;
  /** 节点标识 */
  readonly node: string;
  /** 节点名称（快照） */
  readonly nodeName: string | null;
  /** 这个节点第几轮 */
  readonly round: number;
  /** 会签时是第几个参与人（依次会签按它的先后）；不是会签时为 null */
  readonly signSeq: number | null;
  /** 票签的权重；不是票签时为 null */
  readonly weight: number | null;
  readonly state: WorkItemState;
  readonly assignment: WorkItemAssignment;
  /** 怎样来的：start、forward…… */
  readonly arrival: string;
  /** 收件箱是哪种：role、department、接入方扩展的种类；不在收件箱里时为 null */
  readonly inboxKind: string | null;
  /** 收件箱是哪个角色、部门 */
  readonly inboxId: Id | null;
  /** 当前处理人；在收件箱里等认领时为 null */
  readonly assigneeId: Id | null;
  readonly assigneeName: string | null;
  readonly createdAt: string;
  readonly claimedAt: string | null;
  readonly dueAt: string | null;
  /** 处理结果 */
  readonly outcome: string | null;
  /** 意见 */
  readonly comment: string | null;
  /** 谁处理的 */
  readonly completedBy: Id | null;
  readonly completedByName: string | null;
  /** 处理时间；取消的待办是取消的时间 */
  readonly completedAt: string | null;
}

/** 一条流转记录 */
export interface FlowEvent {
  readonly id: Id;
  readonly instanceId: Id;
  readonly workItemId: Id | null;
  /** `instance.started`、`workItem.completed`、`node.entered`…… */
  readonly type: string;
  readonly node: string | null;
  readonly nodeName: string | null;
  /** 走的连线 */
  readonly edge: string | null;
  readonly outcome: string | null;
  /** 写这条记录时实例的版本：同一步写的几条相同 */
  readonly instanceRev: number;
  /** 操作人；引擎自己推进时为 null */
  readonly actorId: Id | null;
  readonly actorName: string | null;
  readonly at: string;
  /** 意见、原因 */
  readonly comment: string | null;
  readonly data: Readonly<Record<string, unknown>> | null;
}

/** 收件箱的一行：待办加实例的标题与摘要列 */
export interface TodoItem {
  readonly workItem: WorkItem;
  readonly title: string | null;
  readonly summary: Readonly<Record<string, unknown>>;
}
