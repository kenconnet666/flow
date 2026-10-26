/**
 * 查询给前端的形状（0006 第 4 节，同 Java 的 view 包与前端 flow-ui 的 client/types）：发起页、待办详情、实例详情、流程图、管理端的列表。
 */
import type { FlowEvent, Id, ProcessInstance, WorkItem } from '@zerodep-dev/flow-model';
import type { JsonRecord } from '../store/types.js';

/** 能发起的一个流程 */
export interface StartableProcess {
  readonly key: string;
  readonly name: string;
  readonly category: string | null;
  readonly version: number;
}

/** 表单在一个节点上的样子：清单、前端代码、节点上的权限，值里去掉节点上隐藏的字段 */
export interface FormView {
  readonly versionId: Id;
  readonly key: string;
  readonly version: number;
  readonly manifest: JsonRecord;
  readonly source: string | null;
  readonly compiled: string | null;
  readonly compilerVersion: string | null;
  /** 节点上的权限，定义里的 JSON 形状：`{ default, fields }` */
  readonly access: JsonRecord;
  readonly start: boolean;
  readonly values: JsonRecord;
}

/** 提交时要选处理人的下一步（0005 的 5.6）；candidates 为 null 时谁都可以选 */
export interface PickView {
  readonly node: string;
  readonly nodeName: string;
  readonly max: number | null;
  readonly candidates: readonly { readonly id: Id; readonly name: string }[] | null;
}

/** 发起页 */
export interface StartView {
  readonly process: StartableProcess;
  readonly form: FormView | null;
  readonly picks: readonly PickView[];
}

/** 退回时能选的一个节点 */
export interface ReturnTarget {
  readonly node: string;
  readonly nodeName: string;
  /** 上次在那里处理的人（退回去落到他）；没人处理过时为 null */
  readonly handlerName: string | null;
}

/** 一个处理结果：审批面板上的一个按钮 */
export interface OutcomeView {
  readonly key: string;
  readonly label: string;
  readonly kind: string;
  /** required、optional、none */
  readonly comment: string;
  readonly confirm: boolean;
  readonly picks: readonly PickView[];
  readonly targets: readonly ReturnTarget[];
}

/** 处理之外我现在能做的协作 */
export interface WorkItemActions {
  readonly transfer: boolean;
  readonly delegate: boolean;
  readonly addSigners: boolean;
  readonly release: boolean;
  readonly assign: boolean;
  readonly withdrawStep: boolean;
}

/** 待办详情 */
export interface WorkItemView {
  readonly workItem: WorkItem;
  readonly instance: ProcessInstance;
  readonly form: FormView | null;
  readonly outcomes: readonly OutcomeView[];
  readonly claimable: boolean;
  readonly completable: boolean;
  readonly actions: WorkItemActions;
}

/** 流程图上的一个节点；state 是 done、current、pending */
export interface DiagramNode {
  readonly id: string;
  readonly name: string;
  readonly type: string;
  readonly x: number | null;
  readonly y: number | null;
  readonly state: 'done' | 'current' | 'pending';
}

/** 流程图上的一条线 */
export interface DiagramEdge {
  readonly id: string;
  readonly from: string;
  readonly to: string;
  readonly label: string | null;
  readonly kind: string | null;
  readonly taken: boolean;
}

/** 实例的流程图：走到哪了 */
export interface ProcessDiagram {
  readonly nodes: readonly DiagramNode[];
  readonly edges: readonly DiagramEdge[];
}

/** 实例详情 */
export interface InstanceDetail {
  readonly instance: ProcessInstance;
  readonly form: FormView | null;
  readonly workItems: readonly WorkItem[];
  readonly timeline: readonly FlowEvent[];
  /** 我能不能撤回、催办 */
  readonly actions: { readonly withdraw: boolean; readonly remind: boolean };
  /** 跳转能去的节点 */
  readonly nodes: readonly { readonly node: string; readonly name: string; readonly type: string }[];
  readonly diagram: ProcessDiagram;
}

/** 管理端的流程列表的一行 */
export interface ProcessSummary {
  readonly key: string;
  readonly name: string;
  readonly category: string | null;
  readonly status: string;
  readonly latestVersion: number;
}

/** 定义校验的一个问题（0005 第 7 节） */
export interface DefinitionProblem {
  readonly code: string;
  readonly severity: 'error' | 'warning';
  readonly node: string | null;
  readonly edge: string | null;
  readonly lane: string | null;
  readonly field: string | null;
  readonly params: Readonly<Record<string, string>>;
}
