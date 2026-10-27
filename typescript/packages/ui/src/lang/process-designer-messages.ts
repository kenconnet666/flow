/**
 * 流程设计器的文案（组件库 LangMessages 的键 flowDesigner），与 flow-ui 其余的文案分开放：设计器只在管理端用到。
 * 接入方在 Lang.messages.flowDesigner 里改个别字。
 */
import { useMessages, type MessageTable } from '@zerodep-dev/vue-tsx-pro-ui';

/** 设计器上的字 */
export interface FlowDesignerMessages {
  readonly design: string;
  readonly json: string;
  readonly canvas: string;
  readonly palette: string;
  readonly add: (type: string) => string;
  readonly undo: string;
  readonly redo: string;
  readonly remove: string;
  readonly autoLayout: string;
  /** 撤销、重做按钮上说的那一步 */
  readonly steps: Readonly<Record<'move' | 'connect' | 'reconnect' | 'node' | 'edge' | 'process', string>>;
  /** 节点的种类 */
  readonly nodeTypes: Readonly<Record<string, string>>;
  /** 人工节点默认的两个结果 */
  readonly approveOutcome: string;
  readonly rejectOutcome: string;
  /** 新建流程时骨架里的名字 */
  readonly skeleton: Readonly<Record<'process' | 'start' | 'task' | 'end', string>>;
  readonly processPanel: string;
  readonly nodePanel: string;
  readonly edgePanel: string;
  readonly id: string;
  readonly idInvalid: string;
  readonly name: string;
  readonly description: string;
  readonly processKey: string;
  readonly category: string;
  readonly form: string;
  readonly noForm: string;
  readonly titleTemplate: string;
  readonly titleHint: string;
  readonly summary: string;
  readonly withdraw: string;
  readonly withdrawOptions: Readonly<Record<string, string>>;
  readonly emptyAssignee: string;
  readonly emptyAssigneeOptions: Readonly<Record<string, string>>;
  readonly inherit: string;
  readonly assignees: string;
  readonly pickPeople: string;
  readonly userIds: string;
  readonly roleIds: string;
  readonly departmentIds: string;
  readonly initiator: string;
  /** 没记名字的人、角色、部门 */
  readonly orgEntry: (type: string, id: string) => string;
  readonly sameAs: string;
  readonly resolver: string;
  readonly addResolver: string;
  readonly removeResolver: (label: string) => string;
  /** 定义里写着、接入方没注册的扩展名 */
  readonly unregistered: (name: string) => string;
  /** 参数的值取自引用（{ ref }）时显示的 */
  readonly refValue: (ref: string) => string;
  readonly paramRequired: string;
  /** 处理结果上的提交前检查（守卫） */
  readonly guards: string;
  /** 节点的业务信息（meta） */
  readonly nodeMeta: string;
  /** 条件里接入方注册的计算值的分组名 */
  readonly computed: string;
  readonly otherSources: (count: number) => string;
  readonly outcomes: string;
  readonly outcomeKey: string;
  readonly outcomeLabel: string;
  readonly outcomeKind: string;
  readonly outcomeKinds: Readonly<Record<string, string>>;
  readonly commentRequired: string;
  readonly addOutcome: string;
  readonly removeOutcome: (label: string) => string;
  readonly multi: string;
  readonly participants: string;
  readonly multiOptions: Readonly<Record<string, string>>;
  readonly ratio: string;
  readonly votes: string;
  readonly fallback: string;
  readonly due: string;
  readonly noDue: string;
  readonly units: Readonly<Record<string, string>>;
  readonly amount: string;
  readonly allow: string;
  readonly allowOptions: Readonly<Record<string, string>>;
  readonly formAccess: string;
  readonly defaultAccess: string;
  readonly access: Readonly<Record<string, string>>;
  readonly noFormFields: string;
  readonly action: string;
  readonly args: string;
  readonly argsInvalid: string;
  readonly retry: string;
  readonly onFailure: string;
  readonly onFailureOptions: Readonly<Record<string, string>>;
  readonly wait: string;
  readonly event: string;
  readonly receiveOutcomes: string;
  readonly ccTo: string;
  readonly message: string;
  readonly callProcess: string;
  readonly callMode: string;
  readonly callModes: Readonly<Record<string, string>>;
  readonly result: string;
  readonly terminate: string;
  readonly connectTo: string;
  readonly edgeEnds: (from: string, to: string) => string;
  /** 连线的终点（改接到别的节点） */
  readonly edgeTarget: string;
  readonly edgeOutcome: string;
  readonly anyOutcome: string;
  readonly condition: string;
  readonly isDefault: string;
  readonly defaultEdge: string;
  readonly edgeKind: string;
  readonly edgeKinds: Readonly<Record<string, string>>;
  readonly edgeLabel: string;
  readonly problems: string;
  readonly noProblems: string;
  readonly variable: (name: string) => string;
}

export const flowDesignerMessages: MessageTable<FlowDesignerMessages> = {
  'zh-CN': {
    design: '设计',
    json: 'JSON',
    canvas: '流程设计画布',
    palette: '添加节点',
    add: (type) => `添加${type}`,
    undo: '撤销',
    redo: '重做',
    remove: '删除',
    autoLayout: '自动排版',
    steps: { move: '移动节点', connect: '连线', reconnect: '改接连线', node: '改节点属性', edge: '改连线属性', process: '改流程属性' },
    nodeTypes: {
      start: '开始',
      task: '人工审批',
      exclusive: '条件分支',
      parallel: '并行',
      inclusive: '包容分支',
      service: '自动处理',
      timer: '定时',
      receive: '等待事件',
      cc: '抄送',
      call: '子流程',
      end: '结束',
    },
    approveOutcome: '同意',
    rejectOutcome: '不同意',
    skeleton: { process: '新流程', start: '提交', task: '审批', end: '结束' },
    processPanel: '流程',
    nodePanel: '节点',
    edgePanel: '连线',
    id: '标识',
    idInvalid: '标识要以字母开头，只用字母、数字、- 与 _，且不能与别的重复',
    name: '名称',
    description: '说明',
    processKey: '流程标识',
    category: '分类',
    form: '表单',
    noForm: '不用表单',
    titleTemplate: '实例标题',
    titleHint: '花括号里写字段，如 采购申请：{form.title}',
    summary: '待办列表里显示的字段',
    withdraw: '发起人撤回',
    withdrawOptions: { beforeClaim: '有人认领之前', beforeAction: '有人处理之前', never: '不能撤回' },
    emptyAssignee: '找不到处理人时',
    emptyAssigneeOptions: { autoPass: '自动通过', admin: '转给流程管理员', error: '报错停下' },
    inherit: '同流程的设置',
    assignees: '处理人',
    pickPeople: '选人、角色或部门',
    userIds: '用户编号',
    roleIds: '角色编号',
    departmentIds: '部门编号',
    initiator: '发起人',
    orgEntry: (type, id) => `${({ user: '用户', role: '角色', department: '部门' } as Record<string, string>)[type] ?? type} ${id}`,
    sameAs: '与这些节点的处理人相同',
    resolver: '解析器',
    addResolver: '添加解析器',
    removeResolver: (label) => `删除解析器 ${label}`,
    unregistered: (name) => `${name}（未注册）`,
    refValue: (ref) => `取自 ${ref}，在 JSON 里改`,
    paramRequired: '必填',
    guards: '提交前检查',
    nodeMeta: '业务信息',
    computed: '计算值',
    otherSources: (count) => `另有 ${count} 项（取表单字段、上一步选……）在 JSON 里改`,
    outcomes: '处理结果',
    outcomeKey: '标识',
    outcomeLabel: '按钮上的字',
    outcomeKind: '种类',
    outcomeKinds: { none: '普通', approve: '通过', reject: '驳回', return: '退回', escalate: '提级', terminate: '终止' },
    commentRequired: '意见必填',
    addOutcome: '添加结果',
    removeOutcome: (label) => `删除结果 ${label}`,
    multi: '会签',
    participants: '会签的人（每人各办一份）',
    multiOptions: { none: '不会签', all: '全部通过', any: '一人通过即可', sequential: '依次审批', ratio: '按比例通过', votes: '按票数通过' },
    ratio: '通过的比例（0–1）',
    votes: '通过的票数',
    fallback: '比例、票数达不到时的结果',
    due: '时限',
    noDue: '不限时',
    units: { workdays: '工作日', days: '天', hours: '小时', minutes: '分钟' },
    amount: '数量',
    allow: '允许的操作',
    allowOptions: { transfer: '转办', delegate: '委派', addSign: '加签', withdraw: '撤回', remind: '催办' },
    formAccess: '表单字段',
    defaultAccess: '没列出的字段',
    access: { none: '同默认', hidden: '隐藏', readonly: '只读', editable: '可编辑', required: '必填' },
    noFormFields: '流程还没选表单：在"流程"里选',
    action: '动作',
    args: '参数（JSON 对象）',
    argsInvalid: '不是 JSON 对象，没有改',
    retry: '失败后重试几次',
    onFailure: '重试用完之后',
    onFailureOptions: { manual: '等管理员处理（实例不动）', error: '暂停实例' },
    wait: '等多久',
    event: '事件名',
    receiveOutcomes: '事件可能带的结果',
    ccTo: '抄送给',
    message: '附言',
    callProcess: '调用的流程',
    callMode: '方式',
    callModes: { sync: '等子流程结束再往下', async: '不等，直接往下' },
    result: '流程的结果',
    terminate: '到这里整个流程结束（取消其余分支）',
    connectTo: '连到……',
    edgeEnds: (from, to) => `${from} → ${to}`,
    edgeTarget: '连到',
    edgeOutcome: '哪个结果走这条线',
    anyOutcome: '任意结果',
    condition: '条件',
    isDefault: '默认出边：其余的条件都不满足时走这条',
    defaultEdge: '其余',
    edgeKind: '种类',
    edgeKinds: { none: '普通', return: '退回', escalate: '提级' },
    edgeLabel: '线上的文字',
    problems: '问题',
    noProblems: '没有发现问题',
    variable: (name) => `流程变量 ${name}`,
  },
  'en-US': {
    design: 'Design',
    json: 'JSON',
    canvas: 'Process design canvas',
    palette: 'Add node',
    add: (type) => `Add ${type}`,
    undo: 'Undo',
    redo: 'Redo',
    remove: 'Delete',
    autoLayout: 'Auto layout',
    steps: { move: 'move nodes', connect: 'connect', reconnect: 'reconnect', node: 'edit node', edge: 'edit connection', process: 'edit process' },
    nodeTypes: {
      start: 'Start',
      task: 'Approval',
      exclusive: 'Decision',
      parallel: 'Parallel',
      inclusive: 'Inclusive',
      service: 'Automatic',
      timer: 'Timer',
      receive: 'Wait for event',
      cc: 'Copy',
      call: 'Subprocess',
      end: 'End',
    },
    approveOutcome: 'Approve',
    rejectOutcome: 'Reject',
    skeleton: { process: 'New process', start: 'Submit', task: 'Approve', end: 'End' },
    processPanel: 'Process',
    nodePanel: 'Node',
    edgePanel: 'Connection',
    id: 'ID',
    idInvalid: 'Start with a letter, use only letters, digits, - and _, and keep it unique',
    name: 'Name',
    description: 'Description',
    processKey: 'Process key',
    category: 'Category',
    form: 'Form',
    noForm: 'No form',
    titleTemplate: 'Instance title',
    titleHint: 'Fields in braces, e.g. Purchase: {form.title}',
    summary: 'Fields shown in the task list',
    withdraw: 'Starter can withdraw',
    withdrawOptions: { beforeClaim: 'Before anyone claims', beforeAction: 'Before anyone acts', never: 'Never' },
    emptyAssignee: 'When nobody is found',
    emptyAssigneeOptions: { autoPass: 'Pass automatically', admin: 'Send to the process admin', error: 'Stop with an error' },
    inherit: 'Same as the process',
    assignees: 'Assignees',
    pickPeople: 'Pick people, roles or departments',
    userIds: 'User IDs',
    roleIds: 'Role IDs',
    departmentIds: 'Department IDs',
    initiator: 'Starter',
    orgEntry: (type, id) => `${({ user: 'User', role: 'Role', department: 'Department' } as Record<string, string>)[type] ?? type} ${id}`,
    sameAs: 'Same people as these nodes',
    resolver: 'Resolver',
    addResolver: 'Add resolver',
    removeResolver: (label) => `Remove resolver ${label}`,
    unregistered: (name) => `${name} (not registered)`,
    refValue: (ref) => `From ${ref}; edit it in JSON`,
    paramRequired: 'Required',
    guards: 'Checks before submit',
    nodeMeta: 'Business info',
    computed: 'Computed',
    otherSources: (count) => `${count} more (form fields, picked by the previous step…) — edit them in JSON`,
    outcomes: 'Outcomes',
    outcomeKey: 'Key',
    outcomeLabel: 'Button text',
    outcomeKind: 'Kind',
    outcomeKinds: { none: 'Plain', approve: 'Approve', reject: 'Reject', return: 'Return', escalate: 'Escalate', terminate: 'Terminate' },
    commentRequired: 'Comment required',
    addOutcome: 'Add outcome',
    removeOutcome: (label) => `Remove outcome ${label}`,
    multi: 'Multiple approvers',
    participants: 'Approvers (one task each)',
    multiOptions: { none: 'Single', all: 'Everyone approves', any: 'Anyone approves', sequential: 'One after another', ratio: 'By ratio', votes: 'By votes' },
    ratio: 'Ratio to pass (0–1)',
    votes: 'Votes to pass',
    fallback: 'Outcome when the ratio or votes are not reached',
    due: 'Time limit',
    noDue: 'No limit',
    units: { workdays: 'working days', days: 'days', hours: 'hours', minutes: 'minutes' },
    amount: 'Amount',
    allow: 'Allowed actions',
    allowOptions: { transfer: 'Transfer', delegate: 'Delegate', addSign: 'Add signers', withdraw: 'Withdraw', remind: 'Remind' },
    formAccess: 'Form fields',
    defaultAccess: 'Fields not listed',
    access: { none: 'Default', hidden: 'Hidden', readonly: 'Read only', editable: 'Editable', required: 'Required' },
    noFormFields: 'The process has no form yet: choose one under "Process"',
    action: 'Action',
    args: 'Arguments (JSON object)',
    argsInvalid: 'Not a JSON object; not changed',
    retry: 'Retries after a failure',
    onFailure: 'After the retries',
    onFailureOptions: { manual: 'Wait for an admin (instance keeps going)', error: 'Suspend the instance' },
    wait: 'Wait for',
    event: 'Event name',
    receiveOutcomes: 'Outcomes the event may carry',
    ccTo: 'Copy to',
    message: 'Message',
    callProcess: 'Process to call',
    callMode: 'Mode',
    callModes: { sync: 'Wait for it to finish', async: 'Do not wait' },
    result: 'Process result',
    terminate: 'End the whole process here (cancel other branches)',
    connectTo: 'Connect to…',
    edgeEnds: (from, to) => `${from} → ${to}`,
    edgeTarget: 'Goes to',
    edgeOutcome: 'Outcome that takes this line',
    anyOutcome: 'Any outcome',
    condition: 'Condition',
    isDefault: 'Default: taken when no other condition matches',
    defaultEdge: 'otherwise',
    edgeKind: 'Kind',
    edgeKinds: { none: 'Plain', return: 'Return', escalate: 'Escalate' },
    edgeLabel: 'Text on the line',
    problems: 'Problems',
    noProblems: 'No problems found',
    variable: (name) => `Variable ${name}`,
  },
};

declare module '@zerodep-dev/vue-tsx-pro-ui' {
  interface LangMessages {
    flowDesigner: FlowDesignerMessages;
  }
}

/** 当前语言下设计器的文案 */
export const useDesignerMessages = () => useMessages('flowDesigner', flowDesignerMessages);
