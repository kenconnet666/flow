/**
 * 流程定义：纯 JSON，前端设计器、两个后端读同一份（docs/decisions/0005）。这里只有类型；结构索引见 graph.ts，校验见 validate.ts。
 *
 * 概念对应 BPMN 2.0 的子集：泳道、开始与结束、人工节点、排他 / 并行 / 包容网关、自动节点、调用流程、定时等待、等待外部事件、抄送，
 * 加接入方注册的自定义节点（`type` 以 `x-` 开头）。条件是条件树（condition.ts），要逻辑的地方按名字引用接入方注册的扩展。
 */
import type { Condition } from './condition.js';

// ---------- 取值与引用（0005 的 1.1） ----------

/** 取别处的值：`form.字段`、`sys.名字`、流程变量名、`fn.名字` */
export interface Ref {
  readonly ref: string;
}

/** 一个值：直接写，或取别处的值 */
export type Value<T> = T | Ref;

/** 是不是引用 */
export const isRef = (value: unknown): value is Ref =>
  typeof value === 'object' && value !== null && !Array.isArray(value) && typeof (value as Ref).ref === 'string';

/** 时长（0005 第 4 节）：工作日按流程的 `calendar` 算 */
export type Duration =
  | { readonly workdays: number }
  | { readonly days: number }
  | { readonly hours: number }
  | { readonly minutes: number };

/** 时限：一段时长，或到某个时刻（日期、时间的字段） */
export type Due = Duration | { readonly until: Value<string> };

// ---------- 处理人（0005 第 3 节） ----------

/** 接入方组织表里的编号（用户、角色、部门，一般是自增主键）；超出安全整数时写成只有数字的文字 */
export type OrgId = number | `${bigint}`;

/** 是不是一个组织的编号：正的安全整数，或只有数字的文字 */
export function isOrgId(value: unknown): value is OrgId {
  if (typeof value === 'number') return Number.isSafeInteger(value) && value > 0;
  return typeof value === 'string' && /^[1-9][0-9]{0,18}$/.test(value);
}

/**
 * 处理人的来源；`assignees` 里的几个取并集。指定的人、角色、部门、扩展种类可以带 `name`：设计器选人时记下的名字快照，
 * 只给人看（属性面板、JSON），引擎按编号解析、不读它
 */
export type AssigneeSource =
  /** 指定的人；`people` 字段里有几个人就是几个候选 */
  | { readonly user: Value<OrgId>; readonly weight?: number; readonly name?: string }
  /** 角色收件箱：有这个角色的人认领 */
  | { readonly role: Value<OrgId>; readonly name?: string }
  /** 部门下面的人认领 */
  | { readonly department: Value<OrgId>; readonly name?: string }
  /** 发起人 */
  | { readonly initiator: true }
  /** 与某个人工节点最后的处理人相同 */
  | { readonly sameAs: string }
  /** 接入方注册的解析器 */
  | { readonly resolver: string; readonly args?: Readonly<Record<string, unknown>> }
  /** 接入方扩展的候选种类（岗位、项目组……） */
  | { readonly group: string; readonly id: Value<OrgId>; readonly name?: string }
  /** 上一步的处理人提交时选（0005 的 5.6） */
  | { readonly pick: { readonly from?: readonly AssigneeSource[]; readonly max?: number } };

/** 收件箱：角色、部门或扩展的种类，加负责人 */
export type Inbox = (
  | { readonly role: Value<OrgId> }
  | { readonly department: Value<OrgId> }
  | { readonly group: string; readonly id: Value<OrgId> }
) & {
  /** 收件箱的负责人：无人认领时指定承办人、处理改派；不写时调接入方的 `inboxManager` */
  readonly manager?: readonly AssigneeSource[];
};

/** 处理人解析为空时（0005 的 2.1） */
export type EmptyAssignee = 'autoPass' | 'admin' | 'error';

/** 自动跳过 */
export interface AutoSkip {
  /** 处理人和上一步相同 */
  readonly sameAsPrevious?: boolean;
  /** 处理人就是发起人 */
  readonly initiator?: boolean;
}

// ---------- 表单权限（0004 第 4 节）与通知（0005 第 8 节） ----------

/** 字段在一个节点上的权限 */
export type FieldAccess = 'hidden' | 'readonly' | 'editable' | 'required';

/** 节点上的表单权限 */
export interface NodeFormAccess {
  /** 没列出的字段按它；不写时发起节点 `editable`、其他节点 `readonly` */
  readonly default?: FieldAccess;
  /** 按字段名；明细表的列写成 `列表.列` */
  readonly fields?: Readonly<Record<string, FieldAccess>>;
}

/** 通知的事件 */
export type NotifyEvent = 'arrived' | 'reminded' | 'dueSoon' | 'overdue' | 'cc' | 'returned' | 'finished';

/** 通知怎样发：`false` 不发；或换模板、加收件人 */
export type NotifySettings = Readonly<
  Partial<Record<NotifyEvent, false | { readonly template?: string; readonly to?: readonly AssigneeSource[] }>>
>;

// ---------- 节点（0005 第 4、5 节） ----------

/** 节点的共同字段 */
export interface NodeBase {
  /** 标识，流程内唯一 */
  readonly id: string;
  /** 名称 */
  readonly name: string;
  /** 所在泳道 */
  readonly lane?: string;
  /** 说明 */
  readonly description?: string;
  /** 业务附加信息，引擎原样保存不解释 */
  readonly meta?: Readonly<Record<string, unknown>>;
  /** 线下动作：流程图画虚线框，平台上仍要有人回填结果 */
  readonly offline?: boolean;
  /** 覆盖流程级的设置 */
  readonly emptyAssignee?: EmptyAssignee;
  readonly autoSkip?: AutoSkip;
  readonly notify?: NotifySettings;
}

/** 开始；可以写表单在发起时的权限 */
export interface StartNode extends NodeBase {
  readonly type: 'start';
  readonly form?: NodeFormAccess;
}

/** 结束 */
export interface EndNode extends NodeBase {
  readonly type: 'end';
  /** 结束的结果（通过、否决、搁置……） */
  readonly result?: string;
  /** 终止结束：到这里就结束整个流程，并行的其他分支一起取消 */
  readonly terminate?: boolean;
}

/** 处理结果的种类 */
export type OutcomeKind = 'approve' | 'reject' | 'return' | 'escalate' | 'terminate';

/** 填写要求 */
export type Requirement = 'required' | 'optional';

/** 人工节点的一个处理结果（一个按钮） */
export interface Outcome {
  readonly key: string;
  readonly label: string;
  /** 默认 `approve` */
  readonly kind?: OutcomeKind;
  readonly comment?: Requirement;
  readonly attachments?: Requirement;
  /** 会签时一票否决（按比例、按票数时才有意义） */
  readonly veto?: boolean;
  /** 提交前的守卫：接入方注册的名字 */
  readonly guards?: readonly string[];
  /** 点了先确认 */
  readonly confirm?: boolean;
  /** 只用于 `terminate`：终止后实例的结束结果 */
  readonly result?: string;
  /** 只用于 `return`：`"choose"` 时处理时从上游走过的节点里选退回的目标 */
  readonly target?: 'choose';
}

/** 会签的完成规则；接入方可以注册新的规则名 */
export type MultiCompletion = 'all' | 'any' | 'ratio' | 'votes' | 'sequential' | (string & {});

/** 会签 */
export interface MultiInstance {
  /** 参与的人：来源列表，或取一个 `people` 字段、流程变量 */
  readonly participants: readonly AssigneeSource[] | Ref;
  readonly complete: MultiCompletion;
  /** `ratio`：选同一结果的人数达到这个比例（0–1） */
  readonly ratio?: number;
  /** `votes`：选同一结果的票数达到这个数 */
  readonly votes?: number;
  /** `ratio`、`votes`：全部处理完仍没有结果达到门槛时按哪个结果 */
  readonly fallback?: string;
}

/** 提醒 */
export type Reminder = { readonly before: Duration } | { readonly every: Duration; readonly times?: number };

/** 超时怎么办 */
export type Overdue =
  | { readonly action: 'complete'; readonly outcome: string }
  | { readonly action: 'escalate' }
  | { readonly action: 'mark' };

/** 进收件箱后多久没人认领怎么办 */
export interface Unclaimed {
  readonly after: Duration;
  readonly action: 'assignByManager' | 'remind';
}

/** 允许的动作，默认全开 */
export interface AllowedActions {
  readonly transfer?: boolean;
  readonly delegate?: boolean;
  readonly addSign?: boolean;
  readonly withdraw?: boolean;
  readonly remind?: boolean;
}

/** 人工节点 */
export interface TaskNode extends NodeBase {
  readonly type: 'task';
  /** 谁处理；不写时用所在泳道的收件箱 */
  readonly assignees?: readonly AssigneeSource[];
  readonly outcomes: readonly Outcome[];
  readonly multi?: MultiInstance;
  readonly due?: Due;
  readonly reminders?: readonly Reminder[];
  readonly overdue?: Overdue;
  readonly unclaimed?: Unclaimed;
  /** 被退回后重新提交：`restart` 从退回的目标照常走（默认），`resubmit` 直接回到退回它的节点 */
  readonly returnTo?: 'restart' | 'resubmit';
  readonly allow?: AllowedActions;
  readonly form?: NodeFormAccess;
}

/** 网关 */
export interface GatewayNode extends NodeBase {
  readonly type: 'exclusive' | 'parallel' | 'inclusive';
}

/** 自动节点 */
export interface ServiceNode extends NodeBase {
  readonly type: 'service';
  /** 接入方注册的动作名 */
  readonly action: string;
  readonly args?: Readonly<Record<string, unknown>>;
  /** `async`（默认）：提交后由作业执行，可重试；`sync`：在流转的事务里执行 */
  readonly mode?: 'async' | 'sync';
  readonly retry?: { readonly times: number; readonly backoff?: Duration };
  readonly onFailure?: 'manual' | 'error';
}

/** 调用流程 */
export interface CallNode extends NodeBase {
  readonly type: 'call';
  /** 子流程的 key */
  readonly process: string;
  /** `sync`：等子流程结束；`async`：发起后不等 */
  readonly mode?: 'sync' | 'async';
  /** 对列表里的每一项各发起一个子流程 */
  readonly forEach?: Ref;
  /** 循环里那一项的名字 */
  readonly as?: string;
  /** 键是子流程那一侧的名字，值是父流程这一侧的值 */
  readonly input?: Readonly<Record<string, unknown>>;
  /** 键是父流程的流程变量，值是子流程那一侧的名字 */
  readonly output?: Readonly<Record<string, string>>;
}

/** 定时等待 */
export interface TimerNode extends NodeBase {
  readonly type: 'timer';
  readonly wait: Due;
}

/** 等待外部事件 */
export interface ReceiveNode extends NodeBase {
  readonly type: 'receive';
  readonly event: string;
  /** 事件可能带回的结果 */
  readonly outcomes?: readonly string[];
  readonly due?: Due;
  readonly overdue?: Overdue;
  /** 键是流程变量或 `form.字段`，值是事件数据里的名字 */
  readonly output?: Readonly<Record<string, string>>;
}

/** 抄送 */
export interface CcNode extends NodeBase {
  readonly type: 'cc';
  readonly to: readonly AssigneeSource[];
  readonly message?: string;
}

/** 接入方注册的自定义节点：`type` 以 `x-` 开头，其余字段由注册的实现解释 */
export interface CustomNode extends NodeBase {
  readonly type: `x-${string}`;
  readonly [key: string]: unknown;
}

/** 节点 */
export type ProcessNode =
  | StartNode
  | EndNode
  | TaskNode
  | GatewayNode
  | ServiceNode
  | CallNode
  | TimerNode
  | ReceiveNode
  | CcNode
  | CustomNode;

/** 节点的类型 */
export type ProcessNodeType = ProcessNode['type'];

// ---------- 连线（0005 第 6 节） ----------

/** 走这条线时改流程变量 */
export type Effect =
  | { readonly set: string; readonly value: unknown }
  | { readonly increment: string; readonly by?: number };

/** 连线 */
export interface ProcessEdge {
  readonly id: string;
  readonly from: string;
  readonly to: string;
  /** 起点按结果分出边时，哪个结果走这条；不写时任何结果都可以走（再看 when） */
  readonly outcome?: string;
  readonly when?: Condition;
  /** 排他、包容网关的默认出边 */
  readonly default?: boolean;
  /** 退回、提级 */
  readonly kind?: 'return' | 'escalate';
  readonly effects?: readonly Effect[];
  readonly label?: string;
}

// ---------- 顶层（0005 第 2 节） ----------

/** 泳道 */
export interface Lane {
  readonly id: string;
  readonly name: string;
  readonly inbox?: Inbox;
}

/** 流程变量的类型（表单字段类型的一部分） */
export type VariableType = 'text' | 'number' | 'decimal' | 'boolean' | 'date' | 'datetime' | 'select' | 'people';

/** 流程变量的声明 */
export interface VariableDeclaration {
  readonly name: string;
  readonly type: VariableType;
  readonly label?: string;
  readonly default?: unknown;
}

/** 流程级设置 */
export interface ProcessSettings {
  readonly calendar?: string;
  readonly withdraw?: 'beforeClaim' | 'beforeAction' | 'never';
  readonly emptyAssignee?: EmptyAssignee;
  readonly autoSkip?: AutoSkip;
  /** 不在待办里看表单的人对哪些字段隐藏 */
  readonly formView?: Readonly<Record<string, 'hidden' | 'readonly'>>;
  readonly notify?: NotifySettings;
}

/** 布局：引擎不读 */
export interface ProcessLayout {
  readonly nodes?: Readonly<Record<string, { readonly x: number; readonly y: number }>>;
  readonly edges?: Readonly<Record<string, { readonly points?: readonly (readonly [number, number])[] }>>;
}

/** 一个流程（一个版本） */
export interface ProcessDefinition {
  readonly key: string;
  readonly name: string;
  readonly description?: string;
  readonly category?: string;
  /** 发布时后端给；草稿里没有 */
  readonly version?: number;
  /** 一个流程一张表单；`version` 在发布时固定 */
  readonly form?: { readonly key: string; readonly version?: number };
  readonly variables?: readonly VariableDeclaration[];
  /** 待办列表的摘要列：`{ ref }` 的名字 */
  readonly summary?: readonly string[];
  /** 实例标题的模板：花括号里是 `{ ref }` 的名字 */
  readonly title?: string;
  /** 谁能发起；不写时人人可发起 */
  readonly starters?: readonly AssigneeSource[];
  readonly settings?: ProcessSettings;
  readonly lanes?: readonly Lane[];
  readonly nodes: readonly ProcessNode[];
  readonly edges: readonly ProcessEdge[];
  readonly layout?: ProcessLayout;
}

/** 按结果分出边的节点：人工、自动、调用流程、等待外部事件 */
export const BRANCHING_TYPES: ReadonlySet<string> = new Set(['task', 'service', 'call', 'receive']);

/** 网关类型 */
export const GATEWAY_TYPES: ReadonlySet<string> = new Set(['exclusive', 'parallel', 'inclusive']);

/** 内置的节点类型 */
export const BUILTIN_NODE_TYPES: ReadonlySet<string> = new Set([
  'start',
  'end',
  'task',
  'exclusive',
  'parallel',
  'inclusive',
  'service',
  'call',
  'timer',
  'receive',
  'cc',
]);
