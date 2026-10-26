/**
 * 表单容器（FlowForm）与表单代码（defineFlowForm）之间的约定：容器把清单、值、节点权限、人员数据源交给表单，
 * 表单建好之后把"校验、取值"交回容器（审批面板点按钮时用）。
 */
import type { FieldState, FormManifest, NodeFormAccess } from '@zerodep-dev/flow-model';
import type { PeopleSource } from '@zerodep-dev/vue-tsx-pro-ui';

/** 容器交给表单的 */
export interface FlowFormContext {
  /** 字段清单 */
  readonly manifest: FormManifest;
  /** 一开始的值（线上的样子：decimal 是文字） */
  readonly values: Readonly<Record<string, unknown>>;
  /** 节点上的权限（0005 的节点 form）；不在待办里看的人传流程级的 formView */
  readonly nodeAccess: NodeFormAccess | undefined;
  /** 是不是发起：没写 default 时发起能改、其他节点只读 */
  readonly start: boolean;
  /** fill 按权限填写；view 全部只读（实例详情、已处理的待办） */
  readonly mode: 'fill' | 'view';
  /** 人员字段（PeoplePicker）的数据来源 */
  readonly people: PeopleSource | undefined;
  /** 取 `sys.`、流程变量这类不在表单里的引用（显示条件里可能用到）；没有时为 undefined */
  readonly read: (name: string) => unknown;
  /** 表单建好之后把操作交回容器 */
  readonly attach: (handle: FlowFormHandle) => void;
}

/** 表单交回容器的操作 */
export interface FlowFormHandle {
  /** 校验整份表单并显示错误，返回是否通过 */
  validate(): Promise<boolean>;
  /** 现在的值（线上的样子），提交给引擎；引擎只取这个节点上能改的字段 */
  values(): Record<string, unknown>;
  /** 把引擎退回的按字段的问题（flow.validation-failed 的 errors）标到字段上 */
  showErrors(errors: readonly { readonly field?: string; readonly code?: string }[]): void;
}

/** 表单代码拿到的 context：容器给的，加上按字段取权限 */
export interface FlowFormScope extends FlowFormContext {
  /**
   * 字段（明细表的列写成 `列表.列`）在这里的状态：显示、能改、必填。节点权限与显示条件合算，随填写实时变；
   * 自己读写 form.values 的代码用它判断（0004 的 4.4）
   */
  access(path: string): FieldState;
}
