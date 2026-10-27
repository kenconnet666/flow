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
  /** 列表筛选的起止日期 */
  readonly dateFrom: string;
  readonly dateTo: string;
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
}

declare module '@zerodep-dev/vue-tsx-pro-ui' {
  interface LangMessages {
    /** 流程引擎的前端（@zerodep-dev/flow-ui） */
    readonly flow: FlowMessages;
  }
}

const zhProblems: Record<string, string> = {
  required: '必填',
  type: '格式不对',
  minLength: '太短',
  maxLength: '太长',
  pattern: '格式不对',
  min: '太小',
  max: '太大',
  integer: '要是整数',
  scale: '小数位太多',
  option: '不在可选的范围里',
  peopleType: '不能选这一类',
  maxCount: '选得太多',
  maxSize: '文件太大',
  minItems: '行数太少',
  maxItems: '行数太多',
};

const enProblems: Record<string, string> = {
  required: 'Required',
  type: 'Invalid format',
  minLength: 'Too short',
  maxLength: 'Too long',
  pattern: 'Invalid format',
  min: 'Too small',
  max: 'Too large',
  integer: 'Must be a whole number',
  scale: 'Too many decimal places',
  option: 'Not one of the options',
  peopleType: 'This kind cannot be picked',
  maxCount: 'Too many picked',
  maxSize: 'File too large',
  minItems: 'Too few rows',
  maxItems: 'Too many rows',
};

const zhEvents: Record<string, string> = {
  'instance.started': '发起',
  'instance.withdrawn': '撤回',
  'instance.terminated': '终止',
  'instance.finished': '结束',
  'workItem.created': '到达',
  'workItem.claimed': '认领',
  'workItem.completed': '处理',
  'workItem.canceled': '取消',
  'workItem.transferred': '转办',
  'workItem.delegated': '委派',
  'workItem.signersAdded': '加签',
  'workItem.signersRemoved': '减签',
  'workItem.resumed': '回到',
  'workItem.released': '退回收件箱',
  'workItem.reassigned': '改派',
  'workItem.assigned': '指定承办人',
  'workItem.withdrawn': '撤回处理',
  'workItem.reminded': '催办',
  'workItem.overdue': '超时',
  'workItem.unclaimed': '无人认领',
  'instance.commented': '留言',
  'instance.cc': '抄送',
  'signal.received': '收到事件',
  'signal.ignored': '收到不在等的事件',
  'node.overdue': '等待超时',
  'workItem.reassignRequested': '申请改派',
  'instance.dataChanged': '管理员改数据',
  'instance.migrated': '迁到新版本',
  'node.skipped': '自动跳过',
  'job.failed': '自动处理失败',
  'job.skipped': '跳过自动处理',
  'instance.suspended': '暂停',
  'instance.resumed': '恢复',
  'instance.jumped': '跳转到',
};

const enEvents: Record<string, string> = {
  'instance.started': 'Started',
  'instance.withdrawn': 'Withdrawn',
  'instance.terminated': 'Terminated',
  'instance.finished': 'Finished',
  'workItem.created': 'Arrived at',
  'workItem.claimed': 'Claimed',
  'workItem.completed': 'Completed',
  'workItem.canceled': 'Canceled',
  'workItem.transferred': 'Transferred',
  'workItem.delegated': 'Delegated',
  'workItem.signersAdded': 'Added signers at',
  'workItem.signersRemoved': 'Removed a signer at',
  'workItem.resumed': 'Back at',
  'workItem.released': 'Released',
  'workItem.reassigned': 'Reassigned',
  'workItem.assigned': 'Assigned',
  'workItem.withdrawn': 'Took back',
  'workItem.reminded': 'Reminded',
  'workItem.overdue': 'Overdue at',
  'workItem.unclaimed': 'Unclaimed at',
  'instance.commented': 'Commented',
  'instance.cc': 'Copied',
  'signal.received': 'Event received at',
  'signal.ignored': 'Event ignored',
  'node.overdue': 'Timed out at',
  'workItem.reassignRequested': 'Asked for reassignment',
  'instance.dataChanged': 'Data changed by an administrator',
  'instance.migrated': 'Migrated to another version',
  'node.skipped': 'Skipped automatically at',
  'job.failed': 'Automatic step failed at',
  'job.skipped': 'Skipped automatic step',
  'instance.suspended': 'Suspended',
  'instance.resumed': 'Resumed',
  'instance.jumped': 'Jumped to',
};

/** 标题后面接节点名的实例级事件（"跳转到 财务复核""收到事件 等对方盖章"） */
const NODE_EVENTS = new Set(['instance.jumped', 'signal.received', 'node.overdue', 'node.skipped', 'job.failed', 'job.skipped']);

/** 一个问题代号的说明：参数 → 文字 */
type ProblemText = (p: Readonly<Record<string, string>>) => string;

/** 定义的问题代号（0005 第 7 节）的中文说明；英文的 enProblemTexts 按它的代号定类型，少写、多写都编译不过 */
const zhProblemTexts = {
  malformed: (p) => `定义写得不对：${p['reason'] ?? ''}`,
  'form-unknown': (p) => `引用的表单 ${p['key'] ?? ''} 不存在`,
  'form-disabled': (p) => `引用的表单 ${p['key'] ?? ''} 停用了`,
  'field-name-invalid': () => '字段名要以小写字母开头，只能有字母和数字',
  'field-label-missing': () => '字段没写标签（label）',
  'field-type-unknown': (p) => `字段类型 ${p['type'] ?? ''} 不认识`,
  'attribute-invalid': (p) => `属性 ${p['attribute'] ?? ''} 的值不对`,
  'pattern-invalid': () => '正则表达式（pattern）写得不对',
  'options-empty': () => '选项是空的',
  'option-duplicate': (p) => `选项的值 ${p['value'] ?? ''} 重复了`,
  'default-invalid': () => '默认值不合字段的规则',
  'range-invalid': () => '最小值大于最大值',
  'list-nested': () => '明细表里不能再有明细表',
  'list-no-columns': () => '明细表没有列',
  'start-missing': () => '没有开始节点',
  'start-multiple': () => '开始节点不止一个',
  'start-incoming': () => '有线连进开始节点',
  'end-missing': () => '没有结束节点',
  'end-outgoing': () => '结束节点有出边',
  'node-duplicate': () => '节点标识重复',
  'edge-duplicate': () => '连线标识重复',
  'lane-duplicate': () => '泳道标识重复',
  'node-type-unknown': () => '节点类型认不出（自定义类型要以 x- 开头）',
  'lane-unknown': () => '节点的泳道不存在',
  'edge-unknown-node': () => '连线的起点或终点不存在',
  'node-no-outgoing': () => '节点没有出边',
  'node-dead-end': () => '从这个节点走不到结束',
  'node-unreachable': () => '从开始走不到这个节点',
  'outcome-duplicate': () => '处理结果的标识重复',
  'outcome-unrouted': (p) => `处理结果 ${p['outcome'] ?? ''} 没有出边`,
  'return-target-invalid': (p) => `处理结果 ${p['outcome'] ?? ''} 的 target 只能写在退回上，而且只能是 choose`,
  'outcome-choose-automatic': (p) => `超时自动完成、会签的 fallback 不能用要选退回节点的结果 ${p['outcome'] ?? ''}`,
  'edge-unknown-outcome': () => '连线写的处理结果在起点上不存在',
  'edge-outcome-not-branching': () => '起点不按结果分出边，连线却写了 outcome',
  'edge-default-not-gateway': () => '起点不是排他、包容网关，连线却写了 default',
  'gateway-multiple-default': () => '网关有不止一条默认出边',
  'gateway-edge-unconditional': () => '网关的出边既没条件也不是默认',
  'parallel-edge-condition': () => '并行网关的出边写了条件（不起作用）',
  'parallel-useless': () => '并行网关只有一进一出',
  'overdue-unknown-outcome': () => '超时自动完成的结果不存在',
  'task-no-outcome': () => '人工节点没有处理结果',
  'assignee-unknown-node': () => 'sameAs 指的节点不存在或不是人工节点',
  'assignee-missing': () => '人工节点没写处理人，泳道也没有收件箱',
  'gateway-unpaired': () => '并行或包容的分叉没有配对的汇合，或分支跳出了区域',
  'return-not-upstream': () => '退回连线的目标不在上游',
  'escalate-no-inbox': () => '提级连线的目标节点没有收件箱',
  'ref-unknown': (p) => `引用了不存在的 ${p['ref'] ?? ''}（没声明的变量、表单里没有的字段、不存在的节点）`,
  'ref-type-mismatch': (p) => `引用 ${p['ref'] ?? ''} 的类型用不上`,
  'org-id-invalid': () => '用户、角色、部门编号要是正整数',
  'condition-incomplete': () => '条件没写完',
  'condition-fn-in-form': () => '表单的条件里不能用 fn.',
  'multi-no-fallback': () => '按比例、按票数的会签要写 fallback',
  'form-field-unknown': (p) => `节点权限引用了表单里没有的字段 ${p['field'] ?? ''}`,
  'form-required-unfillable': () => '必填字段在发起时是隐藏或只读的',
  'call-cycle': () => '调用流程形成了环',
  'call-unknown-process': () => '调用的流程不存在或已停用',
  'extension-unknown': (p) => `${p['kind'] ?? '扩展'} ${p['name'] ?? ''} 没有注册`,
  'due-unknown-calendar': () => '工作日历不存在',
} satisfies Record<string, ProblemText>;

/** 定义的问题代号的英文说明 */
const enProblemTexts: Record<keyof typeof zhProblemTexts, ProblemText> = {
  malformed: (p) => `The definition is malformed: ${p['reason'] ?? ''}`,
  'form-unknown': (p) => `The form ${p['key'] ?? ''} does not exist`,
  'form-disabled': (p) => `The form ${p['key'] ?? ''} is disabled`,
  'field-name-invalid': () => 'Field names start with a lowercase letter and contain only letters and digits',
  'field-label-missing': () => 'The field has no label',
  'field-type-unknown': (p) => `Unknown field type ${p['type'] ?? ''}`,
  'attribute-invalid': (p) => `Invalid value for ${p['attribute'] ?? ''}`,
  'pattern-invalid': () => 'The regular expression (pattern) is invalid',
  'options-empty': () => 'The options are empty',
  'option-duplicate': (p) => `The option value ${p['value'] ?? ''} is repeated`,
  'default-invalid': () => "The default value breaks the field's rules",
  'range-invalid': () => 'The minimum is greater than the maximum',
  'list-nested': () => 'A detail list cannot contain another list',
  'list-no-columns': () => 'The detail list has no columns',
  'start-missing': () => 'There is no start node',
  'start-multiple': () => 'There is more than one start node',
  'start-incoming': () => 'An edge leads into the start node',
  'end-missing': () => 'There is no end node',
  'end-outgoing': () => 'The end node has outgoing edges',
  'node-duplicate': () => 'Node ids are repeated',
  'edge-duplicate': () => 'Edge ids are repeated',
  'lane-duplicate': () => 'Lane ids are repeated',
  'node-type-unknown': () => 'Unknown node type (custom types start with x-)',
  'lane-unknown': () => "The node's lane does not exist",
  'edge-unknown-node': () => 'The edge starts or ends at a node that does not exist',
  'node-no-outgoing': () => 'The node has no outgoing edge',
  'node-dead-end': () => 'The end cannot be reached from this node',
  'node-unreachable': () => 'This node cannot be reached from the start',
  'outcome-duplicate': () => 'Outcome keys are repeated',
  'outcome-unrouted': (p) => `The outcome ${p['outcome'] ?? ''} has no outgoing edge`,
  'return-target-invalid': (p) => `The outcome ${p['outcome'] ?? ''} may set target only when it sends back, and only to choose`,
  'outcome-choose-automatic': (p) => `Automatic completion and countersign fallbacks cannot use ${p['outcome'] ?? ''}, which asks for a step to send back to`,
  'edge-unknown-outcome': () => 'The edge names an outcome its source does not have',
  'edge-outcome-not-branching': () => 'The edge names an outcome, but its source does not branch by outcome',
  'edge-default-not-gateway': () => 'The edge is marked default, but its source is not an exclusive or inclusive gateway',
  'gateway-multiple-default': () => 'The gateway has more than one default edge',
  'gateway-edge-unconditional': () => 'A gateway edge has neither a condition nor default',
  'parallel-edge-condition': () => 'Conditions on parallel gateway edges have no effect',
  'parallel-useless': () => 'The parallel gateway has one way in and one way out',
  'overdue-unknown-outcome': () => 'The outcome for automatic completion does not exist',
  'task-no-outcome': () => 'The task has no outcomes',
  'assignee-unknown-node': () => 'sameAs names a node that does not exist or is not a task',
  'assignee-missing': () => 'The task has no assignees and its lane has no inbox',
  'gateway-unpaired': () => 'A parallel or inclusive split has no matching join, or a branch leaves the region',
  'return-not-upstream': () => 'The send-back edge does not lead upstream',
  'escalate-no-inbox': () => 'The escalation target has no inbox',
  'ref-unknown': (p) => `Unknown reference ${p['ref'] ?? ''} (an undeclared variable, a field the form lacks, or a missing node)`,
  'ref-type-mismatch': (p) => `The reference ${p['ref'] ?? ''} has a type that cannot be used here`,
  'org-id-invalid': () => 'User, role and department ids must be positive integers',
  'condition-incomplete': () => 'The condition is incomplete',
  'condition-fn-in-form': () => 'Form conditions cannot use fn.',
  'multi-no-fallback': () => 'Countersigns by ratio or by votes need a fallback',
  'form-field-unknown': (p) => `The node permissions name ${p['field'] ?? ''}, which the form does not have`,
  'form-required-unfillable': () => 'A required field is hidden or read-only at the start',
  'call-cycle': () => 'The called processes form a cycle',
  'call-unknown-process': () => 'The called process does not exist or is disabled',
  'extension-unknown': (p) => `${p['kind'] ?? 'Extension'} ${p['name'] ?? ''} is not registered`,
  'due-unknown-calendar': () => 'The work calendar does not exist',
};

/** 认不出的代号显示代号本身 */
const problemText = (texts: Record<string, ProblemText>, code: string, params: Readonly<Record<string, string>>) =>
  Object.hasOwn(texts, code) ? texts[code]!(params) : code;

/** 引擎自动提醒的原因（流转记录里 workItem.reminded 的 data.reason） */
const zhReminders: Record<string, string> = { dueSoon: '即将到期，自动提醒', overdue: '已超时，自动提醒', unclaimed: '很久没人认领，自动提醒' };
const enReminders: Record<string, string> = {
  dueSoon: 'Due soon (automatic)',
  overdue: 'Overdue (automatic)',
  unclaimed: 'Not claimed yet (automatic)',
};

/** 事件数据里的名字（引擎存的快照） */
const text = (value: unknown) => (typeof value === 'string' ? value : null);
const names = (value: unknown) => (Array.isArray(value) ? value.filter((v): v is string => typeof v === 'string') : []);

/** 界面文案：每种语言一份，英文必须有 */
export const flowMessages: MessageTable<FlowMessages> = {
  'zh-CN': {
    inbox: '我的待办',
    columnTitle: '标题',
    columnNode: '环节',
    columnArrivedAt: '到达时间',
    columnAssignee: '处理人',
    startedBy: '发起人',
    startedAt: '发起时间',
    pool: '待认领',
    newItem: '新',
    variables: '流程变量',
    overdue: '已超时',
    open: '办理',
    claim: '认领',
    comment: '意见',
    commentRequired: '请填写意见',
    completed: '已处理',
    notOpen: '这个待办已经处理过或已取消',
    viewOnly: '你不是这个待办的处理人，只能查看',
    confirmTitle: (outcome) => `确定“${outcome}”吗？`,
    confirm: '确定',
    cancel: '取消',
    submit: '提交',
    saveDraft: '暂存',
    formNotPublished: '表单发布之后才能调后端代码的接口',
    dateFrom: '从',
    dateTo: '到',
    calendars: '工作日历',
    calendarsHint: '时限写成工作日时按它算。流程定义的 settings.calendar 写日历的名字，不写用 default；一份都没建时按周一到周五算。',
    calendarPick: '改哪一份',
    calendarName: '名字',
    calendarZone: '时区',
    calendarWorkweek: '一周哪几天上班',
    weekdays: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
    calendarDays: '节假日与调休',
    calendarDay: '日子',
    calendarWorkday: '这天上班（调休补班）',
    calendarNote: '说明',
    calendarAddDay: '加一天',
    calendarSaved: '工作日历已保存',
    save: '保存',
    remove: '删除',
    requestReassign: '申请改派',
    batchClaim: '批量认领',
    batchApprove: '批量同意',
    batchReport: (ok, failed) => (failed ? `做成 ${ok} 条，${failed} 条没做成（已被别人处理、要填表单……），请逐条打开处理` : `做成 ${ok} 条`),
    changeData: '改数据',
    changeVariables: '流程变量（JSON）',
    changeForm: '表单字段（JSON）',
    changeDataHint: '只写要改的名字，值写 null 是清空；改前改后都记进流转记录',
    migrate: '迁移版本',
    migrateHint: '把这个实例换到流程的另一个版本接着走：表单数据、流转记录都留着；正停着的节点在那个版本里没有的，要指定换成哪个。',
    migrateVersion: '迁到哪个版本',
    migrateNodeTo: (node) => `「${node}」换成`,
    endedAt: '结束时间',
    progressWaiting: '正在自动处理，稍后再看',
    progressDue: '预计答复',
    progressNoDue: '没有时限',
    statistics: '统计',
    statsProcesses: '各流程的实例',
    statsRunning: '进行中',
    statsCompleted: '已完成',
    statsStopped: '终止、撤回',
    statsRange: '时间范围',
    lastDays: (days) => `最近 ${days} 天`,
    statsNodes: '各环节的处理',
    statsPickProcess: '选一个流程',
    statsDone: '处理了',
    statsAverageHours: '平均停留（小时）',
    statsOverdueRate: '超时比例',
    statsHandlers: '处理人的工作量',
    statsOpen: '手上还有',
    exportJson: '导出',
    importJson: '导入',
    importNote: (file) => `从 ${file} 导入`,
    imported: '已导入并发布',
    copy: '复制',
    copyName: (name) => `${name}（副本）`,
    copyNote: (key) => `复制自 ${key}`,
    copied: (key) => `已复制成 ${key}`,
    compare: '与已发布版本对比',
    diffNone: (version) => `与 v${version} 没有差别（布局不算）`,
    diffTitle: (version) => `与 v${version} 相比`,
    diffAdded: '加',
    diffRemoved: '删',
    diffChanged: '改',
    diffProcess: '流程属性',
    diffNodes: '节点',
    diffEdges: '连线',
    diffLanes: '泳道',
    formPanelForm: '表单',
    formPanelServer: '后端代码',
    serverCodeHint: '一个 public 类实现 FormHandler（不写 package）：validate 校验、compute 算字段、submit 写业务表、actions 给前端调；可以 @Autowired 应用里的 Bean。',
    serverCodeStart: '从骨架开始',
    compile: '试编译',
    compileOk: '编译通过',
    compileProblem: (line, column, message) => `第 ${line} 行第 ${column} 列：${message}`,
    draftOpened: (by, time) => `接着${by ?? ''}在 ${time} 存的草稿改（还没发布）`,
    discardDraft: '放弃草稿',
    draftSaving: '正在保存草稿…',
    designDraftSaved: (time) => `草稿已保存（${time}）`,
    draftSaveFailed: '草稿没保存上，稍后会再试',
    draftConflict: (by) => `${by ?? '别人'}刚保存过这份草稿，你后来的改动没有保存。`,
    reloadDraft: '载入最新的草稿',
    draftSaved: (time) => `已暂存（${time}）`,
    timeline: '流转记录',
    status: { active: '进行中', suspended: '已暂停', completed: '已完成', terminated: '已终止', withdrawn: '已撤回' },
    event: (type, node, outcome) => {
      const what = zhEvents[type] ?? type;
      if (type === 'workItem.completed') return `${node ?? ''}：${outcome ?? what}`;
      return node && (type.startsWith('workItem.') || NODE_EVENTS.has(type)) ? `${what} ${node}` : what;
    },
    valueProblem: (code) => zhProblems[code] ?? '不合要求',
    formFailed: '表单加载失败',
    noForm: '这个流程没有表单',
    system: '系统',
    addRow: (list) => `添加${list}`,
    removeRow: (row) => `删除第 ${row} 行`,
    doneList: '我已办',
    startedList: '我发起的',
    ccList: '抄送我的',
    columnCcFrom: '抄送人',
    columnMessage: '附言',
    columnCcAt: '抄送时间',
    unread: '未读',
    cc: '抄送',
    pickCcUsers: '抄送给谁',
    ccMessage: '附言',
    ccDone: (count) => `已抄送给 ${count} 人`,
    diagram: '流程图',
    nodeState: { done: '已走过', current: '进行中', pending: '未到' },
    nodeType: { exclusive: '条件分支', parallel: '并行', inclusive: '包容分支', service: '自动处理', timer: '定时', receive: '等待事件', cc: '抄送', call: '子流程' },
    processManage: '流程管理',
    newProcess: '新建流程',
    edit: '编辑',
    enable: '启用',
    disable: '停用',
    processStatus: { active: '启用', disabled: '停用' },
    columnKey: '标识',
    columnCategory: '分类',
    columnVersion: '版本',
    definitionEditor: '流程定义',
    loadVersion: '载入版本',
    formManage: '表单管理',
    newForm: '新建表单',
    formEditor: '表单',
    formMode: '写法',
    formModeManifest: '字段清单（自动排版）',
    formModeDesign: '设计',
    formModeCode: '代码',
    formPreview: '预览',
    formPreviewBlocked: '改好下面的问题后显示预览',
    jsonInvalid: (reason) => `JSON 写得不对：${reason}`,
    syntaxError: (reason) => `代码有语法错误：${reason}`,
    extractProblem: (code) =>
      ({
        'fields-missing': '没有导出 fields（export const fields = defineFields({ … })）',
        'fields-not-define': 'fields 要写成 defineFields({ … })',
        'not-literal': 'defineFields 里只能写字面量（不能用变量、函数调用、展开）',
      })[code] ?? code,
    validate: '校验',
    publish: '发布',
    publishNote: '发布说明',
    published: (version) => `已发布第 ${version} 版`,
    noProblems: '没有问题，可以发布',
    problemAt: (where) => `位置：${where}`,
    problem: (code, params) => problemText(zhProblemTexts, code, params),
    monitor: '流程监控',
    columnProcess: '流程',
    keyword: '标题',
    allStatuses: '全部状态',
    allProcesses: '全部流程',
    adminActions: '管理员操作',
    suspend: '暂停',
    resume: '恢复',
    terminate: '终止',
    terminateConfirm: '终止这个流程？打开着的待办都会取消',
    jump: '跳转',
    jumpTo: '跳到哪个节点',
    reassign: '改派',
    reassignTo: '改派给谁',
    incidents: '异常处理',
    columnError: '原因',
    columnAttempts: '次数',
    columnFailedAt: '失败时间',
    incidentRetry: '重试',
    incidentSkip: '跳过',
    incidentSkipTitle: (node) => `跳过「${node}」，按结果往下走`,
    incidentOutcome: '走哪个结果',
    incidentRetried: '已重新排队，稍后自动执行',
    incidentSkipped: '已跳过，流程往下走了',
    incidentResumeFirst: '流程因为这个异常暂停了：先恢复，再重试或跳过',
    returnTargetTitle: '退回到哪一步',
    returnTargetTo: (handler) => `退回给 ${handler}`,
    pickTitle: '选择下一步的处理人',
    pickFor: (node) => `${node} 的处理人`,
    noPeopleSource: '没有可选的人员数据，请联系管理员',
    columnDue: '截止',
    columnOutcome: '处理结果',
    columnCompletedAt: '处理时间',
    columnStatus: '状态',
    columnCurrent: '当前环节',
    waiting: '这个待办在等委派、加签的人办完，之后回到你手里',
    transfer: '转办',
    delegate: '委派',
    addSigners: '加签',
    release: '退回收件箱',
    assign: '指定承办人',
    withdrawStep: '撤回这一步',
    withdrawStepConfirm: '撤回刚才的处理？下一步的待办会取消，这个待办回到你手里',
    designationList: '待我指定',
    awaitingDesignation: '这个待办很久没人认领，请你指定承办人',
    pickUser: '交给谁',
    pickUsers: '加哪些人',
    addMode: '方式',
    addModes: { before: '前加签（他们先办）', after: '后加签（我办完他们再办）', parallel: '并加（一起办）' },
    reason: '说明',
    releaseConfirm: '放回收件箱让别人认领？你以后不能再认领这一条',
    withdraw: '撤回',
    withdrawConfirm: '撤回这个申请？打开着的待办都会取消',
    remind: '催办',
    reminded: (count) => (count ? `已催办 ${count} 个待办` : '一小时内已经催过了'),
    leaveComment: '留言',
    commentPlaceholder: '写给流程里的人看的话，会记进流转记录',
    handlers: '处理人',
    instanceDetail: '流程详情',
    eventDetail: (type, data) => {
      if (type === 'workItem.signersAdded') {
        const mode = text(data['mode']);
        const label = mode === 'before' ? '前加签' : mode === 'after' ? '后加签' : '并加';
        return `${label}：${names(data['userNames']).join('、')}`;
      }
      if (type === 'workItem.signersRemoved') return text(data['userName']);
      if (type === 'workItem.created') {
        const who = text(data['assigneeName']);
        return who && `处理人 ${who}`;
      }
      if (type === 'workItem.reminded' && data['auto']) return zhReminders[text(data['reason']) ?? ''] ?? '自动提醒';
      if (type === 'workItem.unclaimed') return `转收件箱的负责人指定${text(data['designatorName']) ? `：${text(data['designatorName'])}` : ''}`;
      if (type === 'instance.cc') return `抄送给 ${names(data['userNames']).join('、')}`;
      if (type === 'signal.received' || type === 'signal.ignored') return text(data['event']);
      if (type === 'job.failed') return text(data['error']);
      const to = text(data['toUserName']);
      return to ? `交给 ${to}` : null;
    },
  },
  'en-US': {
    inbox: 'My tasks',
    columnTitle: 'Title',
    columnNode: 'Step',
    columnArrivedAt: 'Arrived',
    columnAssignee: 'Assignee',
    startedBy: 'Started by',
    startedAt: 'Started at',
    pool: 'Unclaimed',
    newItem: 'New',
    variables: 'Process variables',
    overdue: 'Overdue',
    open: 'Open',
    claim: 'Claim',
    comment: 'Comment',
    commentRequired: 'Please write a comment',
    completed: 'Completed',
    notOpen: 'This task has already been completed or canceled',
    viewOnly: 'You are not working on this task; it is read-only',
    confirmTitle: (outcome) => `${outcome}?`,
    confirm: 'OK',
    cancel: 'Cancel',
    submit: 'Submit',
    saveDraft: 'Save draft',
    formNotPublished: 'The form backend code can be called once the form is published',
    dateFrom: 'From',
    dateTo: 'To',
    calendars: 'Work calendars',
    calendarsHint: 'Deadlines in workdays use them. A process names its calendar in settings.calendar (default when missing); with none, Monday to Friday.',
    calendarPick: 'Edit',
    calendarName: 'Name',
    calendarZone: 'Time zone',
    calendarWorkweek: 'Working days of the week',
    weekdays: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'],
    calendarDays: 'Holidays and make-up days',
    calendarDay: 'Day',
    calendarWorkday: 'Working day (make-up)',
    calendarNote: 'Note',
    calendarAddDay: 'Add a day',
    calendarSaved: 'The calendar was saved',
    save: 'Save',
    remove: 'Delete',
    requestReassign: 'Request reassignment',
    batchClaim: 'Claim selected',
    batchApprove: 'Approve selected',
    batchReport: (ok, failed) => (failed ? `${ok} done, ${failed} not done (handled by someone else, form required…); open them one by one` : `${ok} done`),
    changeData: 'Change data',
    changeVariables: 'Process variables (JSON)',
    changeForm: 'Form fields (JSON)',
    changeDataHint: 'Only the names to change; null clears a value. Before and after go into the history',
    migrate: 'Migrate version',
    migrateHint: 'Move this instance to another version of the process: form data and history stay; steps it waits at that the version lacks need a replacement.',
    migrateVersion: 'Target version',
    migrateNodeTo: (node) => `Replace "${node}" with`,
    endedAt: 'Ended at',
    progressWaiting: 'Being processed automatically; check back later',
    progressDue: 'Expected reply',
    progressNoDue: 'No deadline',
    statistics: 'Statistics',
    statsProcesses: 'Instances by process',
    statsRunning: 'Running',
    statsCompleted: 'Completed',
    statsStopped: 'Terminated or withdrawn',
    statsRange: 'Period',
    lastDays: (days) => `Last ${days} days`,
    statsNodes: 'Steps',
    statsPickProcess: 'Pick a process',
    statsDone: 'Done',
    statsAverageHours: 'Average hours',
    statsOverdueRate: 'Overdue',
    statsHandlers: 'Workload',
    statsOpen: 'Open',
    exportJson: 'Export',
    importJson: 'Import',
    importNote: (file) => `Imported from ${file}`,
    imported: 'Imported and published',
    copy: 'Copy',
    copyName: (name) => `${name} (copy)`,
    copyNote: (key) => `Copied from ${key}`,
    copied: (key) => `Copied as ${key}`,
    compare: 'Compare with published',
    diffNone: (version) => `No difference from v${version} (layout ignored)`,
    diffTitle: (version) => `Compared with v${version}`,
    diffAdded: 'Added',
    diffRemoved: 'Removed',
    diffChanged: 'Changed',
    diffProcess: 'Process settings',
    diffNodes: 'Nodes',
    diffEdges: 'Edges',
    diffLanes: 'Lanes',
    formPanelForm: 'Form',
    formPanelServer: 'Backend code',
    serverCodeHint: 'One public class implementing FormHandler (no package): validate, compute fields, submit to business tables, actions for the front end; @Autowired beans work.',
    serverCodeStart: 'Start from a skeleton',
    compile: 'Compile',
    compileOk: 'Compiles',
    compileProblem: (line, column, message) => `Line ${line}, column ${column}: ${message}`,
    draftOpened: (by, time) => `Continuing the draft ${by ? `saved by ${by} ` : ''}at ${time} (not published yet)`,
    discardDraft: 'Discard draft',
    draftSaving: 'Saving the draft…',
    designDraftSaved: (time) => `Draft saved (${time})`,
    draftSaveFailed: 'The draft was not saved; it will be retried',
    draftConflict: (by) => `${by ?? 'Someone else'} saved this draft just now; your later changes were not saved.`,
    reloadDraft: 'Load the latest draft',
    draftSaved: (time) => `Draft saved (${time})`,
    timeline: 'History',
    status: { active: 'In progress', suspended: 'Suspended', completed: 'Completed', terminated: 'Terminated', withdrawn: 'Withdrawn' },
    event: (type, node, outcome) => {
      const what = enEvents[type] ?? type;
      if (type === 'workItem.completed') return `${node ?? ''}: ${outcome ?? what}`;
      return node && (type.startsWith('workItem.') || NODE_EVENTS.has(type)) ? `${what} ${node}` : what;
    },
    valueProblem: (code) => enProblems[code] ?? 'Invalid',
    formFailed: 'The form could not be loaded',
    noForm: 'This process has no form',
    system: 'System',
    addRow: (list) => `Add to ${list}`,
    removeRow: (row) => `Remove row ${row}`,
    doneList: 'Done by me',
    startedList: 'Started by me',
    ccList: 'Copied to me',
    columnCcFrom: 'From',
    columnMessage: 'Message',
    columnCcAt: 'Copied at',
    unread: 'Unread',
    cc: 'Copy to',
    pickCcUsers: 'People to copy',
    ccMessage: 'Message',
    ccDone: (count) => `Copied to ${count} people`,
    diagram: 'Process diagram',
    nodeState: { done: 'done', current: 'in progress', pending: 'not reached' },
    nodeType: { exclusive: 'Decision', parallel: 'Parallel', inclusive: 'Inclusive', service: 'Automatic', timer: 'Timer', receive: 'Wait for event', cc: 'Copy', call: 'Subprocess' },
    processManage: 'Processes',
    newProcess: 'New process',
    edit: 'Edit',
    enable: 'Enable',
    disable: 'Disable',
    processStatus: { active: 'Enabled', disabled: 'Disabled' },
    columnKey: 'Key',
    columnCategory: 'Category',
    columnVersion: 'Version',
    definitionEditor: 'Process definition',
    loadVersion: 'Load version',
    formManage: 'Forms',
    newForm: 'New form',
    formEditor: 'Form',
    formMode: 'Written as',
    formModeManifest: 'Field list (auto layout)',
    formModeDesign: 'Design',
    formModeCode: 'Code',
    formPreview: 'Preview',
    formPreviewBlocked: 'Fix the problems below to see the preview',
    jsonInvalid: (reason) => `Invalid JSON: ${reason}`,
    syntaxError: (reason) => `Syntax error: ${reason}`,
    extractProblem: (code) =>
      ({
        'fields-missing': 'The code does not export fields (export const fields = defineFields({ … }))',
        'fields-not-define': 'fields must be written as defineFields({ … })',
        'not-literal': 'defineFields takes literals only (no variables, calls or spreads)',
      })[code] ?? code,
    validate: 'Validate',
    publish: 'Publish',
    publishNote: 'Release note',
    published: (version) => `Published version ${version}`,
    noProblems: 'No problems; ready to publish',
    problemAt: (where) => `At ${where}`,
    problem: (code, params) => problemText(enProblemTexts, code, params),
    monitor: 'Process monitor',
    columnProcess: 'Process',
    keyword: 'Title',
    allStatuses: 'All statuses',
    allProcesses: 'All processes',
    adminActions: 'Administration',
    suspend: 'Suspend',
    resume: 'Resume',
    terminate: 'Terminate',
    terminateConfirm: 'Terminate this process? Open tasks will be canceled',
    jump: 'Jump',
    jumpTo: 'Jump to step',
    reassign: 'Reassign',
    reassignTo: 'Reassign to',
    incidents: 'Incidents',
    columnError: 'Error',
    columnAttempts: 'Attempts',
    columnFailedAt: 'Failed at',
    incidentRetry: 'Retry',
    incidentSkip: 'Skip',
    incidentSkipTitle: (node) => `Skip "${node}" and move on`,
    incidentOutcome: 'Continue with outcome',
    incidentRetried: 'Queued again; it runs shortly',
    incidentSkipped: 'Skipped; the process moved on',
    incidentResumeFirst: 'The process was suspended by this failure: resume it first, then retry or skip',
    returnTargetTitle: 'Return to which step',
    returnTargetTo: (handler) => `Goes back to ${handler}`,
    pickTitle: 'Choose who handles the next step',
    pickFor: (node) => `Assignee for ${node}`,
    noPeopleSource: 'No people directory is available; contact your administrator',
    columnDue: 'Due',
    columnOutcome: 'Outcome',
    columnCompletedAt: 'Completed',
    columnStatus: 'Status',
    columnCurrent: 'Current step',
    waiting: 'This task is waiting for the people you delegated to or added; it comes back to you after',
    transfer: 'Transfer',
    delegate: 'Delegate',
    addSigners: 'Add signers',
    release: 'Release',
    assign: 'Assign',
    withdrawStep: 'Take back',
    withdrawStepConfirm: 'Take back what you just did? The next step is canceled and this task comes back to you',
    designationList: 'Awaiting my assignment',
    awaitingDesignation: 'Nobody has claimed this task; please assign someone',
    pickUser: 'Hand to',
    pickUsers: 'People to add',
    addMode: 'Mode',
    addModes: { before: 'Before me (they go first)', after: 'After me', parallel: 'Alongside me' },
    reason: 'Note',
    releaseConfirm: 'Put this back for someone else to claim? You will not be able to claim it again',
    withdraw: 'Withdraw',
    withdrawConfirm: 'Withdraw this request? Open tasks will be canceled',
    remind: 'Remind',
    reminded: (count) => (count ? `Reminded ${count} task(s)` : 'Already reminded within the hour'),
    leaveComment: 'Comment',
    commentPlaceholder: 'A note for the people in this process; it goes into the history',
    handlers: 'Assignees',
    instanceDetail: 'Process details',
    eventDetail: (type, data) => {
      if (type === 'workItem.signersAdded') {
        const mode = text(data['mode']);
        const label = mode === 'before' ? 'Before' : mode === 'after' ? 'After' : 'Alongside';
        return `${label}: ${names(data['userNames']).join(', ')}`;
      }
      if (type === 'workItem.signersRemoved') return text(data['userName']);
      if (type === 'workItem.created') {
        const who = text(data['assigneeName']);
        return who && `Assigned to ${who}`;
      }
      if (type === 'workItem.reminded' && data['auto']) return enReminders[text(data['reason']) ?? ''] ?? 'Automatic reminder';
      if (type === 'workItem.unclaimed') return 'Handed to the inbox manager';
      if (type === 'instance.cc') return `To ${names(data['userNames']).join(', ')}`;
      if (type === 'signal.received' || type === 'signal.ignored') return text(data['event']);
      if (type === 'job.failed') return text(data['error']);
      const to = text(data['toUserName']);
      return to ? `To ${to}` : null;
    },
  },
};

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
