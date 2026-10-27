/**
 * 引擎 HTTP 接口的请求与结果（0006 的 7.3）：两个后端相同。运行时的形状（实例、待办、流转记录）在模型包，
 * 这里是查询页面用的视图（发起页、待办详情）与命令的参数。编号是 bigint（见模型包的 runtime.ts）。
 */
import type {
  FlowEvent,
  FormManifest,
  Id,
  Moment,
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
  /** 发起时要选处理人的第一步 */
  readonly picks: readonly PickView[];
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
}

/** 流程图：实例按的那个版本的节点与连线，加上走到哪了 */
export interface ProcessDiagram {
  readonly nodes: readonly {
    readonly id: string;
    readonly name: string | null;
    readonly type: string;
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

/** 管理端的流程列表的一行 */
export interface ProcessSummary {
  readonly key: string;
  readonly name: string;
  readonly category: string | null;
  /** active 能发起，其余停用 */
  readonly status: string;
  readonly latestVersion: number;
}

/** 校验定义找出的一个问题（0005 第 7 节的问题代号） */
export interface DefinitionProblem {
  readonly code: string;
  /** error 不能发布，warning 多半是画漏了 */
  readonly severity: 'error' | 'warning';
  readonly node: string | null;
  readonly edge: string | null;
  readonly lane: string | null;
  readonly field: string | null;
  readonly params: Readonly<Record<string, string>>;
}

/** 流程的一个版本 */
export interface DefinitionVersion {
  readonly version: number;
  readonly note: string | null;
  readonly publishedByName: string | null;
  readonly publishedAt: Moment;
  readonly formKey: string | null;
  readonly formVersion: number | null;
}

/** 发布的结果 */
export interface PublishedDefinition {
  readonly id: Id;
  readonly key: string;
  readonly version: number;
}

/** 管理端的表单列表的一行 */
export interface FormSummary {
  readonly key: string;
  readonly name: string;
  readonly category: string | null;
  /** active 能被流程选用，disabled 停用 */
  readonly status: string;
  readonly latestVersion: number;
}

/** 表单版本列表的一行 */
export interface FormVersion {
  readonly version: number;
  readonly note: string | null;
  readonly publishedByName: string | null;
  readonly publishedAt: Moment;
}

/** 表单某个版本的全部内容（编辑时载入） */
export interface FormDocument {
  readonly key: string;
  readonly name: string;
  readonly category: string | null;
  readonly version: number;
  /** 字段清单的 JSON 文字 */
  readonly manifest: string;
  /** 前端源码；只有字段清单、自动排版的表单为 null */
  readonly clientSource: string | null;
  readonly serverLanguage: string | null;
  readonly serverSource: string | null;
}

/** 发布一张表单（版本号由后端给） */
export interface FormPublication {
  readonly key: string;
  readonly name: string;
  readonly category?: string;
  /** 字段清单（0004 的 1.3） */
  readonly manifest: FormManifest;
  /** 前端源码（代码表单）；只有清单时不给 */
  readonly clientSource?: string;
  readonly note?: string;
}

/** 发布表单的结果 */
export interface PublishedForm {
  readonly id: Id;
  readonly key: string;
  readonly version: number;
}

/** 管理端搜实例的条件：不写的不限 */
export interface InstanceSearch {
  readonly status?: string;
  readonly processKey?: string;
  readonly keyword?: string;
}

/** 异常（管理端）：重试用完的作业，多半是自动节点调外部系统失败 */
export interface Incident {
  readonly jobId: Id;
  /** 作业的种类：action 自动节点的动作、timer、overdue…… */
  readonly kind: string;
  readonly instanceId: Id;
  readonly instanceTitle: string | null;
  readonly processKey: string | null;
  /** 卡在哪个节点 */
  readonly node: string | null;
  readonly nodeName: string | null;
  /** 试了几次 */
  readonly attempts: number;
  /** 最后一次失败的原因 */
  readonly error: string | null;
  /** 最后一次尝试的时间 */
  readonly failedAt: Moment;
  /** 能跳过、按结果往下走（自动节点的动作，实例还停在那里） */
  readonly skippable: boolean;
  /** 跳过时能选的结果；空的时不用选 */
  readonly outcomes: readonly string[];
}

/** 跳过失败的作业（管理员） */
export interface SkipJobCommand {
  readonly jobId: Id;
  /** 走哪个结果的出边；节点只有一条没写结果的出边时不填 */
  readonly outcome?: string;
  readonly reason?: string;
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
