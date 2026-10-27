/**
 * 设计端用的：流程与表单的列表、版本、草稿、发布，校验出的问题，接入方注册的扩展（扩展目录）。
 */
import type { FormManifest, Id, Moment } from '@zerodep-dev/flow-model';

/** 管理端的流程列表的一行 */
export interface ProcessSummary {
  readonly key: string;
  readonly name: string;
  readonly category: string | null;
  /** active 能发起，其余停用 */
  readonly status: string;
  readonly latestVersion: number;
}

/** 一个选项：参数的可选值、动作或自定义节点可能给的结果 */
export interface ExtensionChoice {
  readonly value: string;
  readonly label: string;
}

/** 扩展的一个参数（或节点业务信息的一个字段）：设计器按类型画控件 */
export interface ExtensionParam {
  readonly key: string;
  readonly label: string | null;
  /** text、number、decimal、boolean、date、select */
  readonly type: string;
  readonly required: boolean;
  readonly description: string | null;
  readonly options: readonly ExtensionChoice[];
}

/** 注册的一个扩展：名字与接入方在 info() 里给的显示名、参数、结果 */
export interface ExtensionEntry {
  /** 定义里写的名字（自定义节点是 x- 开头的类型） */
  readonly name: string;
  readonly label: string;
  readonly description: string | null;
  readonly params: readonly ExtensionParam[];
  readonly outcomes: readonly ExtensionChoice[];
  /** 计算值的类型；别的扩展为 null */
  readonly valueType: string | null;
}

/** 接入方注册了哪些扩展（后端的 Extensions.catalog）：设计器给下拉、画参数与节点业务信息的控件，校验时认得这些名字 */
export interface ExtensionCatalog {
  readonly resolvers: readonly ExtensionEntry[];
  readonly guards: readonly ExtensionEntry[];
  readonly actions: readonly ExtensionEntry[];
  /** 计算值：条件里的 fn.名字 */
  readonly computed: readonly ExtensionEntry[];
  /** 自定义节点类型 */
  readonly nodes: readonly ExtensionEntry[];
  /** 会签的完成规则 */
  readonly completions: readonly ExtensionEntry[];
  readonly fieldTypes: readonly ExtensionEntry[];
  /** 节点的业务信息有哪些字段（写进节点的 meta）；没声明时为空 */
  readonly nodeMeta: readonly ExtensionParam[];
  /** 能不能在线写表单的后端代码 */
  readonly formServerCode: boolean;
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

/** 设计中的草稿（流程设计器、表单编辑器自动保存，发布后删掉） */
export interface DesignDraft {
  readonly kind: DraftKind;
  readonly targetKey: string;
  readonly name: string | null;
  /** 设计器的内容：流程是定义的 JSON，表单是源码与清单；列表里不带（为 null） */
  readonly content: string | null;
  /** 基于哪个已发布的版本改的；新建的为 null */
  readonly baseVersion: number | null;
  /** 版本：下次保存时带上，别人先存过时保存报 flow.concurrent-modification */
  readonly rev: number;
  readonly updatedBy: Id | null;
  readonly updatedByName: string | null;
  readonly updatedAt: Moment;
}

/** 草稿是哪一种 */
export type DraftKind = 'process' | 'form';

/** 保存草稿 */
export interface SaveDesignDraftCommand {
  readonly kind: DraftKind;
  readonly key: string;
  readonly name?: string;
  readonly content: string;
  readonly baseVersion?: number | null;
  /** 上次读到、存到的版本；第一次保存不写 */
  readonly rev?: number | null;
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

/** 用了某张表单的流程（最新发布的版本引用它） */
export interface FormUsage {
  readonly processKey: string;
  readonly processName: string;
  readonly version: number;
}

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
  /** 前端源码的编译结果与编译器版本（browser-compile 的 COMPILER_VERSION）：审批页面直接执行，不用再编译 */
  readonly clientCompiled?: string;
  readonly compilerVersion?: string;
  /** 后端代码的语言（现在只有 java）与源码；没有后端代码时不给 */
  readonly serverLanguage?: 'java';
  readonly serverSource?: string;
  readonly note?: string;
  /** 编辑时看到的最新版本（新建时 0）；已经不是最新的时不发布，不给时不核对 */
  readonly expectedLatest?: number;
}

/** 试编译后端代码报出的一个问题：行列从 1 开始 */
export interface CompileProblem {
  readonly line: number;
  readonly column: number;
  readonly message: string;
}

/** 发布表单的结果 */
export interface PublishedForm {
  readonly id: Id;
  readonly key: string;
  readonly version: number;
}
