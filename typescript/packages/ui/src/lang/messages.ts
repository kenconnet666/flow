/**
 * flow-ui 的文案：界面上的字（收件箱、审批面板、表单）与 flow 的业务错误码（0008）。
 *
 * 界面文案挂在组件库的 LangMessages 上（键 flow），按当前语言取，接入方在 Lang.messages.flow 里改个别字；
 * 错误码的文案要并进组件库的 Lang.errorCodes，请求出错时组件库才认得：
 *
 *   createUi({ lang: withFlowLang(zhCN) })   // 或 defineLang(zhCN, { errorCodes: flowErrorCodes['zh-CN'] })
 */
import { defineLang, useMessages, type ErrorCodeText, type Lang, type MessageTable } from '@zerodep-dev/vue-tsx-pro-ui';
import type { FlowErrorCode } from '@zerodep-dev/flow-model';
import { enUS } from './en-US';
import { zhCN } from './zh-CN';

/** flow-ui 界面上的字 */
export interface FlowMessages {
  /** 收件箱的标题 */
  readonly inbox: string;
  /** 收件箱的列 */
  readonly columnTitle: string;
  readonly columnNode: string;
  readonly columnArrivedAt: string;
  readonly columnAssignee: string;
  /** 发起人、发起时间 */
  readonly startedBy: string;
  readonly startedAt: string;
  /** 在收件箱里等认领 */
  readonly pool: string;
  /** 附件还没传完就提交 */
  readonly uploading: string;
  /** 代理人代不在岗的人处理：处理人一栏写的 */
  readonly onBehalfOf: (assignee: string, principal: string) => string;
  /** 实例详情里的流程变量（只给流程管理员） */
  readonly variables: string;
  /** 落到我身上、还没打开过的待办 */
  readonly newItem: string;
  /** 过了截止时间 */
  readonly overdue: string;
  /** 打开一个待办的按钮 */
  readonly open: string;
  /** 认领 */
  readonly claim: string;
  /** 意见 */
  readonly comment: string;
  /** 意见必填时的提示 */
  readonly commentRequired: string;
  /** 处理完了 */
  readonly completed: string;
  /** 待办已经处理过、取消了：不能再操作 */
  readonly notOpen: string;
  /** 我不是处理人也不是候选：只能看 */
  readonly viewOnly: string;
  /** 确认对话框 */
  readonly confirmTitle: (outcome: string) => string;
  readonly confirm: string;
  readonly cancel: string;
  /** 发起 */
  readonly submit: string;
  /** 暂存：填了一半先存 */
  readonly saveDraft: string;
  /** 预测的下一步 */
  readonly nextAfterSubmit: (where: string) => string;
  readonly nextAfterOutcome: (label: string, where: string) => string;
  readonly nextInbox: string;
  readonly nextPick: string;
  readonly nextEnd: (result: string) => string;
  /** 委派、加签给我的待办：我办的结果只是意见，交回原来的处理人定 */
  readonly opinionFor: (holder: string) => string;
  /** 发起前的草稿 */
  readonly startDraftSaved: string;
  readonly startDrafts: string;
  readonly continueDraft: string;
  readonly draftSavedAt: (at: string) => string;
  /** 列表筛选的日期区间 */
  readonly dateRange: string;
  /** 列表筛选：只看已归档的 */
  readonly archivedOnly: string;
  /** 工作日历 */
  readonly calendars: string;
  readonly calendarsHint: string;
  readonly calendarPick: string;
  readonly calendarName: string;
  readonly calendarZone: string;
  readonly calendarWorkweek: string;
  readonly weekdays: readonly string[];
  readonly calendarDays: string;
  readonly calendarDay: string;
  readonly calendarWorkday: string;
  readonly calendarNote: string;
  readonly calendarAddDay: string;
  readonly calendarSaved: string;
  readonly save: string;
  readonly remove: string;
  /** 申请改派、批量、改数据 */
  readonly requestReassign: string;
  readonly batchClaim: string;
  readonly batchApprove: string;
  readonly batchReport: (ok: number, failed: number) => string;
  readonly changeData: string;
  readonly changeVariables: string;
  readonly changeForm: string;
  readonly changeDataHint: string;
  /** 实例迁到另一个版本 */
  readonly migrate: string;
  readonly migrateHint: string;
  readonly migrateVersion: string;
  readonly migrateNodeTo: (node: string) => string;
  /** 外部参与方看的进度 */
  readonly endedAt: string;
  readonly progressWaiting: string;
  readonly progressDue: string;
  readonly progressNoDue: string;
  /** 统计 */
  readonly statistics: string;
  readonly statsProcesses: string;
  readonly statsRunning: string;
  readonly statsCompleted: string;
  readonly statsStopped: string;
  readonly statsRange: string;
  readonly lastDays: (days: number) => string;
  readonly statsNodes: string;
  readonly statsPickProcess: string;
  readonly statsDone: string;
  readonly statsAverageHours: string;
  readonly statsOverdueRate: string;
  readonly statsHandlers: string;
  readonly statsOpen: string;
  /** 导出、导入、复制 */
  readonly exportJson: string;
  readonly importJson: string;
  readonly importNote: (file: string) => string;
  readonly imported: string;
  readonly copy: string;
  readonly copyName: (name: string) => string;
  readonly copyNote: (key: string) => string;
  readonly copied: (key: string) => string;
  /** 与已发布版本对比 */
  readonly compare: string;
  readonly diffNone: (version: number) => string;
  readonly diffTitle: (version: number) => string;
  readonly diffAdded: string;
  readonly diffRemoved: string;
  readonly diffChanged: string;
  readonly diffProcess: string;
  readonly diffNodes: string;
  readonly diffEdges: string;
  readonly diffLanes: string;
  /** 表单编辑器的两个页签与后端代码 */
  readonly formPanelForm: string;
  readonly formPanelServer: string;
  readonly serverCodeHint: string;
  readonly serverCodeStart: string;
  readonly compile: string;
  readonly compileOk: string;
  readonly compileProblem: (line: number, column: number, message: string) => string;
  /** 表单还没发布（编辑器的预览）时调后端接口 */
  readonly formNotPublished: string;
  /** 设计中的草稿（流程、表单编辑器） */
  readonly draftOpened: (by: string | null, time: string) => string;
  readonly discardDraft: string;
  readonly draftSaving: string;
  readonly designDraftSaved: (time: string) => string;
  readonly draftSaveFailed: string;
  readonly draftConflict: (by: string | null) => string;
  readonly reloadDraft: string;
  readonly draftSaved: (time: string) => string;
  /** 流转记录 */
  readonly timeline: string;
  /** 实例的状态 */
  readonly status: Readonly<Record<string, string>>;
  /** 结束的结果（结束节点、终止的处理结果上写的 result）；认得的显示在状态的位置，比"已完成"说得清楚 */
  readonly results: Readonly<Record<string, string>>;
  /** 流转记录里一条事件的标题 */
  readonly event: (type: string, node: string | null, outcome: string | null) => string;
  /** 表单的值不合：按问题代号（必填、类型、范围……） */
  readonly valueProblem: (code: string) => string;
  /** 表单加载失败（编译、执行出错） */
  readonly formFailed: string;
  /** 没有表单 */
  readonly noForm: string;
  /** 系统（引擎自己推进） */
  readonly system: string;
  /** 明细表：加一行 */
  readonly addRow: (list: string) => string;
  /** 明细表：删掉第几行 */
  readonly removeRow: (row: number) => string;
  /** 我已办、我发起的、抄送我的列表的标题 */
  readonly doneList: string;
  readonly startedList: string;
  readonly ccList: string;
  /** 抄送我的：抄送人（抄送节点时是节点名）、附言、抄送时间、未读 */
  readonly columnCcFrom: string;
  readonly columnMessage: string;
  readonly columnCcAt: string;
  readonly unread: string;
  /** 抄送的按钮与对话框 */
  readonly cc: string;
  readonly pickCcUsers: string;
  readonly ccMessage: string;
  /** 抄送之后的提示 */
  readonly ccDone: (count: number) => string;
  /** 流程图 */
  readonly diagram: string;
  /** 流程图上节点的状态 */
  readonly nodeState: Readonly<Record<string, string>>;
  /** 流程图上节点的种类（人工节点、开始、结束不写） */
  readonly nodeType: Readonly<Record<string, string>>;
  /** 流程管理（管理端） */
  readonly processManage: string;
  readonly newProcess: string;
  readonly edit: string;
  readonly enable: string;
  readonly disable: string;
  readonly processStatus: Readonly<Record<string, string>>;
  readonly columnKey: string;
  readonly columnCategory: string;
  readonly columnVersion: string;
  /** 表单管理（管理端） */
  readonly formManage: string;
  readonly newForm: string;
  readonly formEditor: string;
  readonly formMode: string;
  readonly formModeManifest: string;
  readonly formModeDesign: string;
  readonly formModeCode: string;
  readonly formPreview: string;
  /** 清单有问题、取不出时预览的位置 */
  readonly formPreviewBlocked: string;
  readonly jsonInvalid: (reason: string) => string;
  readonly syntaxError: (reason: string) => string;
  /** 从代码里取字段清单的问题（fields-missing、fields-not-define、not-literal） */
  readonly extractProblem: (code: string) => string;
  /** 定义编辑 */
  readonly definitionEditor: string;
  readonly loadVersion: string;
  readonly validate: string;
  readonly publish: string;
  readonly publishNote: string;
  readonly published: (version: number) => string;
  /** 发布时撞了别人的：标识已被占用（key-taken），或打开后别人又发布过（newer-version） */
  readonly publishConflict: (reason: 'key-taken' | 'newer-version', latest: number, by: string | null) => string;
  readonly noProblems: string;
  readonly problemAt: (where: string) => string;
  /** 0005 第 7 节的问题代号的说明；认不出的显示代号 */
  readonly problem: (code: string, params: Readonly<Record<string, string>>) => string;
  /** 流程监控（管理端） */
  readonly monitor: string;
  readonly columnProcess: string;
  readonly keyword: string;
  readonly allStatuses: string;
  readonly allProcesses: string;
  /** 管理员的操作 */
  readonly adminActions: string;
  readonly suspend: string;
  readonly resume: string;
  readonly terminate: string;
  readonly terminateConfirm: string;
  readonly jump: string;
  readonly jumpTo: string;
  readonly reassign: string;
  readonly reassignTo: string;
  /** 异常处理：列表的标题与列、重试、跳过 */
  readonly incidents: string;
  readonly columnError: string;
  readonly columnAttempts: string;
  readonly columnFailedAt: string;
  readonly incidentRetry: string;
  readonly incidentSkip: string;
  readonly incidentSkipTitle: (node: string) => string;
  readonly incidentOutcome: string;
  readonly incidentRetried: string;
  readonly incidentSkipped: string;
  /** 发件箱的投递（作业 deliver）失败：没有节点，环节一栏写它；跳过叫"放弃投递" */
  readonly incidentDelivery: string;
  readonly incidentDiscard: string;
  readonly incidentDiscardTitle: string;
  readonly incidentDiscarded: string;
  /** 实例因为异常暂停了（onFailure: error） */
  readonly incidentResumeFirst: string;
  /** 退回时选退回到哪一步 */
  readonly returnTargetTitle: string;
  readonly returnTargetTo: (handler: string) => string;
  /** 选下一步的处理人 */
  readonly pickTitle: string;
  readonly pickFor: (node: string) => string;
  /** 能选任何人、却没给人员数据来源 */
  readonly noPeopleSource: string;
  /** 列表的列：截止时间、处理结果、处理时间、状态、当前环节 */
  readonly columnDue: string;
  readonly columnOutcome: string;
  readonly columnCompletedAt: string;
  readonly columnStatus: string;
  readonly columnCurrent: string;
  /** 待办在等委派、加签的人办完 */
  readonly waiting: string;
  /** 协作的按钮与对话框（转办、委派、加签、退回收件箱） */
  readonly transfer: string;
  readonly delegate: string;
  readonly addSigners: string;
  readonly release: string;
  /** 收件箱负责人指定承办人 */
  readonly assign: string;
  /** 撤回刚处理的一步 */
  readonly withdrawStep: string;
  readonly withdrawStepConfirm: string;
  /** 等我指定承办人的列表 */
  readonly designationList: string;
  /** 待办详情：这一条没人认领，等我指定承办人 */
  readonly awaitingDesignation: string;
  /** 协作对话框里的字段 */
  readonly pickUser: string;
  readonly pickUsers: string;
  readonly addMode: string;
  readonly addModes: Readonly<Record<'before' | 'after' | 'parallel', string>>;
  readonly reason: string;
  /** 退回收件箱前的确认 */
  readonly releaseConfirm: string;
  /** 实例详情上的操作：撤回、催办、留言 */
  readonly withdraw: string;
  readonly withdrawConfirm: string;
  readonly remind: string;
  /** 催办之后的提示：催到了几个待办（一小时内催过的不再催） */
  readonly reminded: (count: number) => string;
  readonly leaveComment: string;
  readonly commentPlaceholder: string;
  /** 实例详情：当前在谁手里 */
  readonly handlers: string;
  /** 实例详情的标题 */
  readonly instanceDetail: string;
  /** 流转记录里事件的说明：交给了谁、加了谁（没有时为 null） */
  readonly eventDetail: (type: string, data: Readonly<Record<string, unknown>>) => string | null;
  /** 留言时选 @ 谁 */
  readonly mentionPlaceholder: string;
}

declare module '@zerodep-dev/vue-tsx-pro-ui' {
  interface LangMessages {
    /** 流程引擎的前端（@zerodep-dev/flow-ui） */
    readonly flow: FlowMessages;
  }
}

/** 界面文案：每种语言一份（zh-CN.ts、en-US.ts），英文必须有 */
export const flowMessages: MessageTable<FlowMessages> = { 'zh-CN': zhCN, 'en-US': enUS };

/** 当前语言下 flow-ui 的文案 */
export const useFlowMessages = () => useMessages('flow', flowMessages);

/** flow 的业务错误码的文案：每种语言一份 */
export const flowErrorCodes: MessageTable<Readonly<Record<FlowErrorCode, ErrorCodeText>>> = {
  'zh-CN': {
    'flow.not-found': '要找的流程、待办或实例不存在',
    'flow.forbidden': '没有权限做这件事',
    'flow.not-candidate': '你不是这个待办的处理人',
    'flow.already-claimed': '这个待办已经被别人认领了',
    'flow.work-item-not-open': '这个待办已经处理过了',
    'flow.instance-not-active': '这个流程已经结束了',
    'flow.concurrent-modification': '数据刚被别人改过，请刷新后再试',
    'flow.guard-rejected': '条件不满足，不能这样处理',
    'flow.validation-failed': '填写的内容有问题，请检查标出的地方',
    'flow.definition-invalid': '流程定义有问题，不能发布',
    'flow.extension-failed': '扩展出错了',
    'flow.internal-error': '流程引擎出错了，请稍后再试',
  },
  'en-US': {
    'flow.not-found': 'The process, task or instance does not exist',
    'flow.forbidden': 'You are not allowed to do this',
    'flow.not-candidate': 'You are not working on this task',
    'flow.already-claimed': 'Someone else has claimed this task',
    'flow.work-item-not-open': 'This task has already been completed',
    'flow.instance-not-active': 'This process has already ended',
    'flow.concurrent-modification': 'Someone else just changed this; refresh and try again',
    'flow.guard-rejected': 'This outcome is not allowed right now',
    'flow.validation-failed': 'Some fields need attention',
    'flow.definition-invalid': 'The process definition has problems and cannot be published',
    'flow.extension-failed': 'An extension failed',
    'flow.internal-error': 'The workflow engine failed; try again later',
  },
};

/** 在一种语言上加上 flow 的错误码文案（没有这种语言时用英文） */
export function withFlowLang(lang: Lang): Lang {
  const language = lang.code.split('-')[0];
  const codes =
    flowErrorCodes[lang.code] ??
    Object.entries(flowErrorCodes).find(([code]) => code.split('-')[0] === language)?.[1] ??
    flowErrorCodes['en-US'];
  return defineLang(lang, { errorCodes: codes });
}
