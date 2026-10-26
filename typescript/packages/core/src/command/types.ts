/**
 * 引擎的命令（0006 第 3 节，同 Java 的 command 包）：每个命令是一个普通对象，进同一条管道（权限 → 拦截器 → 标准处理，一个事务）。
 * 编号都是 bigint；表单的值与流程变量是 JSON 形状的对象。
 */
import type { FlowEvent, Id, ProcessInstance, WorkItem } from '@zerodep-dev/flow-model';
import type { JsonRecord } from '../store/types.js';

/** 一个命令改了什么：前端据此刷新，不用再查一次 */
export interface CommandResult {
  /** 实例的新状态 */
  readonly instance: ProcessInstance;
  /** 新生成的待办 */
  readonly created: readonly WorkItem[];
  /** 这次结束的待办（处理完的、取消的），是改之前的样子 */
  readonly closed: readonly WorkItem[];
  /** 这次写的流转记录 */
  readonly events: readonly FlowEvent[];
}

/** 下一步要这一步选处理人的节点 → 选的人（0005 的 5.6） */
export type Picks = Readonly<Record<string, readonly Id[]>>;

/** 发起：按最新发布的版本走 */
export interface StartCommand {
  readonly key: string;
  /** 开始节点上能改的字段 */
  readonly form?: JsonRecord;
  /** 流程变量的初值（盖过定义里的默认值） */
  readonly variables?: JsonRecord;
  readonly businessKey?: string | null;
  /** 显式给的标题；不给时按定义的模板生成 */
  readonly title?: string | null;
  readonly priority?: number;
  readonly picks?: Picks;
}

/** 认领收件箱里的待办 */
export interface ClaimCommand {
  readonly workItemId: Id;
}

/** 处理一个待办；待办还在收件箱里时，候选人处理就等于先认领 */
export interface CompleteCommand {
  readonly workItemId: Id;
  /** 节点上写的一个结果；节点没写结果时不填 */
  readonly outcome?: string | null;
  readonly comment?: string | null;
  /** 这个节点上能改的字段 */
  readonly form?: JsonRecord;
  readonly picks?: Picks;
  /** 退回到哪个节点：结果是 `{ kind: "return", target: "choose" }` 时必填 */
  readonly returnTarget?: string | null;
}

/** 发起人撤回整个实例 */
export interface WithdrawCommand {
  readonly instanceId: Id;
  readonly reason?: string | null;
}

/** 发布一张表单（版本号由后端给） */
export interface FormPublication {
  readonly key: string;
  readonly name: string;
  readonly category?: string | null;
  /** 字段清单的 JSON 文字 */
  readonly manifest: string;
  readonly clientSource?: string | null;
  readonly clientCompiled?: string | null;
  readonly compilerVersion?: string | null;
  readonly serverLanguage?: string | null;
  readonly serverSource?: string | null;
  readonly note?: string | null;
}
