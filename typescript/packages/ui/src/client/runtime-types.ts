/**
 * 发起、办理、实例详情用的视图与命令的参数（0006 的 7.3）。运行时的形状（实例、待办、流转记录）在模型包；编号是 bigint（见模型包的 runtime.ts）。
 */
import type { FlowEvent, FormManifest, Id, Moment, NodeFormAccess, ProcessInstance, WorkItem } from '@zerodep-dev/flow-model';

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

/** 预测的下一步：选了某个结果（或发起）后流程会停在哪里，按现在的值算，不跑自动节点 */
export interface NextStep {
  readonly node: string;
  readonly nodeName: string | null;
  /** task、service、call、receive、timer、end…… */
  readonly type: string;
  /** 看得出来的处理人名字（指定的人、发起人、同某节点的上一位处理人） */
  readonly handlers: readonly string[];
  /** 进收件箱等人认领 */
  readonly inbox: boolean;
  /** 要在提交时选处理人；不用选时为 null */
  readonly pick: PickView | null;
}

export interface StartView {
  readonly process: StartableProcess;
  /** 开始节点上的表单；流程没有表单时为 null */
  readonly form: FormView | null;
  /** 发起时要选处理人的第一步 */
  readonly picks: readonly PickView[];
  /** 发起后流程会停在哪里 */
  readonly next: readonly NextStep[];
}

/** 提交时要选处理人的下一步（0005 的 5.6） */
export interface PickView {
  readonly node: string;
  readonly nodeName: string | null;
  /** 最多选几个；null 时不限 */
  readonly max: number | null;
  /** 能选的人；null 时谁都可以选（用接入方给的人员数据来源） */
  readonly candidates: readonly { readonly id: Id; readonly name: string }[] | null;
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
  /** 选了这个结果后要选处理人的下一步 */
  readonly picks: readonly PickView[];
  /** 选了这个结果后流程会停在哪里；我不能处理时是空的 */
  readonly next: readonly NextStep[];
  /** 退回时能选的节点（结果写了 target: "choose"）；别的结果是空的 */
  readonly targets: readonly ReturnTarget[];
}

/** 退回时能选的一个节点 */
export interface ReturnTarget {
  readonly node: string;
  readonly nodeName: string | null;
  /** 上次在那里处理的人（退回去落到他） */
  readonly handlerName: string | null;
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
  /** 我暂存过的（表单里已经是暂存的值）；没有时为 null */
  readonly draft: WorkItemDraft | null;
}

/** 待办上我暂存过的：写了一半的意见与暂存的时间 */
export interface WorkItemDraft {
  readonly comment: string | null;
  readonly savedAt: Moment;
}

/** 暂存：填了一半先存，不提交 */
export interface SaveDraftCommand {
  readonly workItemId: Id;
  readonly form?: Readonly<Record<string, unknown>>;
  readonly comment?: string;
}

/** 外部参与方看的进度 */
export interface ProgressView {
  readonly title: string | null;
  readonly processKey: string;
  readonly status: string;
  readonly startedAt: Moment;
  readonly endedAt: Moment | null;
  readonly steps: readonly {
    readonly nodeName: string | null;
    readonly handlerName: string | null;
    readonly arrivedAt: Moment;
    readonly dueAt: Moment | null;
  }[];
}

/** 批量认领、处理时一个待办的结果 */
export interface BatchResult {
  readonly workItemId: Id;
  readonly ok: boolean;
  /** 没做成时的业务错误码 */
  readonly code: string | null;
}

/** 处理之外我现在能做的协作：能处理、节点上的 allow 没关掉时才能做 */
export interface WorkItemActions {
  /** 申请改派（负责人指定给我的） */
  readonly requestReassign?: boolean;
  /** 转办 */
  readonly transfer: boolean;
  /** 委派 */
  readonly delegate: boolean;
  /** 加签 */
  readonly addSigners: boolean;
  /** 退回收件箱（我从收件箱里认领的） */
  readonly release: boolean;
  /** 指定承办人（没人认领、在等我这个收件箱负责人指定） */
  readonly assign: boolean;
  /** 撤回刚处理的一步（我处理的、下一步还没人动） */
  readonly withdrawStep: boolean;
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
  /** 流程的节点（开始节点、网关除外）：管理员跳转时选目标 */
  readonly nodes: readonly { readonly node: string; readonly name: string | null; readonly type: string }[];
  /** 流程图：节点走到哪了、连线走过没有 */
  readonly diagram: ProcessDiagram;
  /** 流程变量，按定义里声明的先后：只给流程管理员，别人是 null */
  readonly variables: readonly VariableView[] | null;
}

/** 实例上的一个流程变量 */
export interface VariableView {
  readonly name: string;
  readonly label: string | null;
  readonly type: string;
  /** 现在的值（typed JSON 还原的强类型）；没有值时为 null */
  readonly value: unknown;
}

/** 流程图：实例按的那个版本的节点与连线，加上走到哪了 */
export interface ProcessDiagram {
  readonly nodes: readonly {
    readonly id: string;
    readonly name: string | null;
    readonly type: string;
    /** 自定义节点注册时给的显示名；内置类型为 null（按前端的文案写） */
    readonly typeLabel: string | null;
    /** 布局里的位置；没画过时为 null（前端自动排） */
    readonly x: number | null;
    readonly y: number | null;
    /** done 走过、current 正停着、pending 还没到 */
    readonly state: string;
  }[];
  readonly edges: readonly {
    readonly id: string;
    readonly from: string;
    readonly to: string;
    readonly label: string | null;
    /** return、escalate，或 null */
    readonly kind: string | null;
    readonly taken: boolean;
  }[];
}

/** 调表单后端代码给前端的接口 */
export interface FormActionCommand {
  readonly formVersionId: Id;
  /** 在实例里调时给；发起页上不给 */
  readonly instanceId?: Id | null;
  readonly action: string;
  readonly args?: Readonly<Record<string, unknown>>;
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
  /** 第一步要选处理人的节点 → 选的人 */
  readonly picks?: Readonly<Record<string, readonly Id[]>>;
  /** 从这份发起前的草稿发起：用草稿那个实例，没提交的字段用草稿里的值（这时 key 可以不写） */
  readonly draftId?: Id;
}

/** 发起前存草稿：第一次存时没有 draftId，存完从结果（状态为 draft 的实例）里拿 */
export interface SaveStartDraftCommand {
  readonly draftId?: Id;
  readonly key?: string;
  /** 开始节点上能改的字段；不查必填，整份替换上次存的 */
  readonly form?: Readonly<Record<string, unknown>>;
  readonly businessKey?: string;
}

/** 处理 */
export interface CompleteCommand {
  readonly workItemId: Id;
  readonly outcome: string;
  readonly comment?: string;
  readonly form?: Readonly<Record<string, unknown>>;
  /** 下一步要选处理人的节点 → 选的人 */
  readonly picks?: Readonly<Record<string, readonly Id[]>>;
  /** 退回到哪个节点：结果的 targets 不空时必填 */
  readonly returnTarget?: string;
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

/** 外部事件：按实例编号或业务单据编号找在等它的实例 */
export interface SignalCommand {
  readonly instanceId?: Id;
  readonly businessKey?: string;
  readonly event: string;
  readonly outcome?: string;
  readonly data?: Readonly<Record<string, unknown>>;
}

/** 抄送（知会） */
export interface CcCommand {
  readonly instanceId: Id;
  readonly users: readonly Id[];
  readonly message?: string;
}

/** 留言 */
export interface CommentCommand {
  readonly instanceId: Id;
  readonly text: string;
  /** @ 的人：他们收到通知（mentioned），名字记进流转记录 */
  readonly mentions?: readonly Id[];
}

/** 管理员对实例：终止、暂停（resume 只要 instanceId） */
export interface InstanceCommand {
  readonly instanceId: Id;
  readonly reason?: string;
}
