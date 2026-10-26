/**
 * 引擎 HTTP 接口的请求与结果（0006 的 7.3）：两个后端相同。运行时的形状（实例、待办、流转记录）在模型包，
 * 这里是查询页面用的视图（发起页、待办详情）与命令的参数。编号是 bigint（见模型包的 runtime.ts）。
 */
import type {
  FlowEvent,
  FormManifest,
  Id,
  NodeFormAccess,
  ProcessInstance,
  WorkItem,
} from '@zerodep-dev/flow-model';

/** 我能发起的一个流程 */
export interface StartableProcess {
  readonly key: string;
  readonly name: string;
  readonly category: string | null;
  /** 最新发布的版本号 */
  readonly version: number;
}

/**
 * 表单在某个位置（发起、某个待办）的样子：清单、前端代码、节点上的权限与值。
 * 值里已经去掉节点上隐藏的字段；显示条件由前端按实时的值合算（模型包的 fieldStates）。
 */
export interface FormView {
  /** 表单版本的编号 */
  readonly versionId: Id;
  readonly key: string;
  readonly version: number;
  readonly manifest: FormManifest;
  /** 前端源码；没有时为 null */
  readonly source: string | null;
  /** 编译好的代码；没有时为 null */
  readonly compiled: string | null;
  /** 编译它的编译器版本 */
  readonly compilerVersion: string | null;
  /** 节点上的权限 */
  readonly access: NodeFormAccess;
  /** 是不是发起：没写 default 时发起能改、其他节点只读 */
  readonly start: boolean;
  readonly values: Readonly<Record<string, unknown>>;
}

/** 发起页 */
export interface StartView {
  readonly process: StartableProcess;
  /** 开始节点上的表单；流程没有表单时为 null */
  readonly form: FormView | null;
}

/** 一个处理结果：审批面板上的一个按钮 */
export interface OutcomeView {
  readonly key: string;
  readonly label: string;
  /** approve、reject、return、escalate、terminate */
  readonly kind: string;
  /** 意见：required 必填、optional 可写、none 不收 */
  readonly comment: 'required' | 'optional' | 'none';
  /** 点了先确认 */
  readonly confirm: boolean;
}

/** 待办详情 */
export interface WorkItemView {
  readonly workItem: WorkItem;
  readonly instance: ProcessInstance;
  readonly form: FormView | null;
  readonly outcomes: readonly OutcomeView[];
  /** 我能认领 */
  readonly claimable: boolean;
  /** 我能处理（落到我的；或在收件箱里、我是候选——处理时顺带认领） */
  readonly completable: boolean;
  /** 处理之外我现在能做的协作 */
  readonly actions: WorkItemActions;
}

/** 处理之外我现在能做的协作：能处理、节点上的 allow 没关掉时才能做 */
export interface WorkItemActions {
  /** 转办 */
  readonly transfer: boolean;
  /** 委派 */
  readonly delegate: boolean;
  /** 加签 */
  readonly addSigners: boolean;
  /** 退回收件箱（我从收件箱里认领的） */
  readonly release: boolean;
}

/** 实例详情（发起人看进度、历史里打开） */
export interface InstanceDetail {
  readonly instance: ProcessInstance;
  /** 表单：都只读，按流程级的 formView 去掉隐藏的字段；流程没有表单时为 null */
  readonly form: FormView | null;
  /** 全部待办，按到达的先后 */
  readonly workItems: readonly WorkItem[];
  /** 流转记录，按先后 */
  readonly timeline: readonly FlowEvent[];
  /** 我现在能对实例做的事（留言随时能留） */
  readonly actions: InstanceActions;
}

/** 我现在能对实例做的事 */
export interface InstanceActions {
  /** 撤回：我是发起人、实例进行中、定义的 withdraw 规则还允许 */
  readonly withdraw: boolean;
  /** 催办：我是发起人、实例进行中、有打开着的待办 */
  readonly remind: boolean;
}

/** 命令的结果：这次改了什么，页面据此刷新 */
export interface CommandResult {
  readonly instance: ProcessInstance;
  /** 新生成的待办 */
  readonly workItems: readonly WorkItem[];
  /** 结束的待办 */
  readonly closed: readonly WorkItem[];
  /** 这一步的流转记录 */
  readonly events: readonly FlowEvent[];
}

/** 发起 */
export interface StartCommand {
  readonly key: string;
  readonly form?: Readonly<Record<string, unknown>>;
  readonly variables?: Readonly<Record<string, unknown>>;
  readonly businessKey?: string;
  readonly title?: string;
  readonly priority?: number;
}

/** 处理 */
export interface CompleteCommand {
  readonly workItemId: Id;
  readonly outcome: string;
  readonly comment?: string;
  readonly form?: Readonly<Record<string, unknown>>;
}

/** 撤回 */
export interface WithdrawCommand {
  readonly instanceId: Id;
  readonly reason?: string;
}

/** 转办、委派、改派：交给一个人 */
export interface HandOverCommand {
  readonly workItemId: Id;
  readonly userId: Id;
  readonly reason?: string;
}

/** 加签的方式：前加签（加的人先办）、后加签（我办完加的人再办）、并加（一起办） */
export type AddSignMode = 'before' | 'after' | 'parallel';

/** 加签 */
export interface AddSignersCommand {
  readonly workItemId: Id;
  readonly users: readonly Id[];
  readonly mode: AddSignMode;
  readonly reason?: string;
}

/** 减签：取消自己加的、还没办的人 */
export interface RemoveSignersCommand {
  readonly workItemId: Id;
  readonly users: readonly Id[];
}

/** 退回收件箱 */
export interface ReleaseCommand {
  readonly workItemId: Id;
  readonly reason?: string;
}

/** 催办：不给 workItemId 时催实例上所有打开着的待办 */
export interface RemindCommand {
  readonly instanceId: Id;
  readonly workItemId?: Id;
  readonly message?: string;
}

/** 留言 */
export interface CommentCommand {
  readonly instanceId: Id;
  readonly text: string;
}

/** 管理员对实例：终止、暂停（resume 只要 instanceId） */
export interface InstanceCommand {
  readonly instanceId: Id;
  readonly reason?: string;
}

/** 管理员跳转：从指定的节点重新走 */
export interface JumpCommand {
  readonly instanceId: Id;
  readonly node: string;
  readonly reason?: string;
}
