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
  'workItem.reminded': '催办',
  'workItem.overdue': '超时',
  'workItem.unclaimed': '无人认领',
  'instance.commented': '留言',
  'instance.cc': '抄送',
  'signal.received': '收到事件',
  'signal.ignored': '收到不在等的事件',
  'node.overdue': '等待超时',
  'job.failed': '自动处理失败',
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
  'workItem.reminded': 'Reminded',
  'workItem.overdue': 'Overdue at',
  'workItem.unclaimed': 'Unclaimed at',
  'instance.commented': 'Commented',
  'instance.cc': 'Copied',
  'signal.received': 'Event received at',
  'signal.ignored': 'Event ignored',
  'node.overdue': 'Timed out at',
  'job.failed': 'Automatic step failed at',
  'instance.suspended': 'Suspended',
  'instance.resumed': 'Resumed',
  'instance.jumped': 'Jumped to',
};

/** 标题后面接节点名的实例级事件（"跳转到 财务复核""收到事件 等对方盖章"） */
const NODE_EVENTS = new Set(['instance.jumped', 'signal.received', 'node.overdue', 'job.failed']);

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
      if (type === 'workItem.unclaimed') return '转收件箱的负责人指定';
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
