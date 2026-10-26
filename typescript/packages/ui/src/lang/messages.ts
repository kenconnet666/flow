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
};

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
    status: { active: '进行中', completed: '已完成', terminated: '已终止', withdrawn: '已撤回' },
    event: (type, node, outcome) => {
      const what = zhEvents[type] ?? type;
      if (type === 'workItem.completed') return `${node ?? ''}：${outcome ?? what}`;
      return node && type.startsWith('workItem.') ? `${what} ${node}` : what;
    },
    valueProblem: (code) => zhProblems[code] ?? '不合要求',
    formFailed: '表单加载失败',
    noForm: '这个流程没有表单',
    system: '系统',
    addRow: (list) => `添加${list}`,
    removeRow: (row) => `删除第 ${row} 行`,
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
    status: { active: 'In progress', completed: 'Completed', terminated: 'Terminated', withdrawn: 'Withdrawn' },
    event: (type, node, outcome) => {
      const what = enEvents[type] ?? type;
      if (type === 'workItem.completed') return `${node ?? ''}: ${outcome ?? what}`;
      return node && type.startsWith('workItem.') ? `${what} ${node}` : what;
    },
    valueProblem: (code) => enProblems[code] ?? 'Invalid',
    formFailed: 'The form could not be loaded',
    noForm: 'This process has no form',
    system: 'System',
    addRow: (list) => `Add to ${list}`,
    removeRow: (row) => `Remove row ${row}`,
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
