/**
 * 表单设计器的文案（组件库 LangMessages 的键 flowFormDesigner）：只在管理端用到，与 flow-ui 其余的文案分开放。
 * 接入方在 Lang.messages.flowFormDesigner 里改个别字。
 */
import { useMessages, type MessageTable } from '@zerodep-dev/vue-tsx-pro-ui';

/** 表单设计器上的字 */
export interface FlowFormDesignerMessages {
  readonly design: string;
  readonly preview: string;
  /** 只读预览：看审批人、抄送人打开时的样子 */
  readonly previewReadonly: string;
  readonly undo: string;
  readonly redo: string;
  readonly remove: string;
  readonly canvas: string;
  readonly palette: string;
  /** 面板的分类：layout、display、field；接入方的分类名原样显示 */
  readonly groups: Readonly<Record<string, string>>;
  /** 字段类型 */
  readonly fieldTypes: Readonly<Record<string, string>>;
  readonly fields: string;
  readonly unplaced: string;
  readonly placedTwice: (name: string) => string;
  readonly addField: string;
  readonly removeField: string;
  readonly removeFieldConfirm: (label: string) => string;
  /** 新字段的标签 */
  readonly newFieldLabel: (n: number) => string;
  readonly newOption: (n: number) => string;
  /** 源码有语法错误时：设计视图停在上次能解析的结果 */
  readonly syntaxError: (message: string) => string;
  readonly noRoot: string;
  readonly nothingSelected: string;
  readonly codeBlock: string;
  readonly codeBlockHint: string;
  readonly fieldGroup: string;
  readonly controlGroup: string;
  readonly componentGroup: string;
  readonly fieldName: string;
  readonly fieldNameInvalid: string;
  readonly label: string;
  readonly description: string;
  readonly required: string;
  readonly control: string;
  readonly content: string;
  readonly maxLength: string;
  readonly min: string;
  readonly max: string;
  readonly integer: string;
  readonly scale: string;
  readonly multiple: string;
  readonly options: string;
  readonly optionsHint: string;
  readonly maxPeople: string;
  readonly visibleWhen: string;
  readonly requiredWhen: string;
  readonly fieldNotLiteral: string;
  readonly unknownField: (name: string) => string;
  /** 代码里写了面板不认识的属性 */
  readonly otherProps: (n: number) => string;
  readonly codeValue: string;
  readonly dropHint: string;
}

export const flowFormDesignerMessages: MessageTable<FlowFormDesignerMessages> = {
  'zh-CN': {
    design: '设计',
    preview: '预览',
    previewReadonly: '只读预览',
    undo: '撤销',
    redo: '重做',
    remove: '删除',
    canvas: '画布',
    palette: '组件',
    groups: { layout: '布局', display: '展示', field: '字段' },
    fieldTypes: {
      text: '文字',
      number: '数字',
      decimal: '金额',
      boolean: '是否',
      date: '日期',
      datetime: '时间',
      select: '选项',
      people: '人员',
      file: '附件',
      list: '明细表',
    },
    fields: '字段',
    unplaced: '未摆放',
    placedTwice: (name) => `字段 ${name} 摆了不止一次`,
    addField: '加字段',
    removeField: '删除字段',
    removeFieldConfirm: (label) => `删除字段“${label}”？画布上摆着的也一起删掉。`,
    newFieldLabel: (n) => `字段 ${n}`,
    newOption: (n) => `选项 ${n}`,
    syntaxError: (message) => `代码有语法错误，改好之前不能设计：${message}`,
    noRoot: '没有找到 defineFlowForm 渲染的 <form.Form>，请在代码里改',
    nothingSelected: '在画布上点一个组件改它的属性；从左边拖组件、字段进画布',
    codeBlock: '代码块',
    codeBlockHint: '这一块是自己写的代码，能移动、删除，内容在代码里改',
    fieldGroup: '字段',
    controlGroup: '控件',
    componentGroup: '属性',
    fieldName: '字段名',
    fieldNameInvalid: '字母开头，只能有字母和数字，不能和别的字段重名',
    label: '标签',
    description: '说明',
    required: '必填',
    control: '控件',
    content: '内容',
    maxLength: '最多几个字',
    min: '最小',
    max: '最大',
    integer: '只能是整数',
    scale: '小数位',
    multiple: '多选',
    options: '选项',
    optionsHint: '一行一个，写成“值=名字”',
    maxPeople: '最多几个',
    visibleWhen: '显示条件',
    requiredWhen: '必填条件',
    fieldNotLiteral: '这个字段的定义不是字面量，请在代码里改',
    unknownField: (name) => `清单里没有字段 ${name}`,
    otherProps: (n) => `代码里还设置了 ${n} 个属性`,
    codeValue: '代码',
    dropHint: '拖到这里',
  },
  'en-US': {
    design: 'Design',
    preview: 'Preview',
    previewReadonly: 'Read-only preview',
    undo: 'Undo',
    redo: 'Redo',
    remove: 'Delete',
    canvas: 'Canvas',
    palette: 'Components',
    groups: { layout: 'Layout', display: 'Display', field: 'Fields' },
    fieldTypes: {
      text: 'Text',
      number: 'Number',
      decimal: 'Amount',
      boolean: 'Yes / no',
      date: 'Date',
      datetime: 'Date and time',
      select: 'Choice',
      people: 'People',
      file: 'Files',
      list: 'Table',
    },
    fields: 'Fields',
    unplaced: 'Not placed',
    placedTwice: (name) => `Field ${name} is placed more than once`,
    addField: 'Add field',
    removeField: 'Delete field',
    removeFieldConfirm: (label) => `Delete the field "${label}"? Where it is placed on the canvas goes too.`,
    newFieldLabel: (n) => `Field ${n}`,
    newOption: (n) => `Option ${n}`,
    syntaxError: (message) => `The code has a syntax error; fix it to keep designing: ${message}`,
    noRoot: 'No <form.Form> rendered by defineFlowForm was found; change it in the code',
    nothingSelected: 'Click a component on the canvas to edit it; drag components and fields in from the left',
    codeBlock: 'Code block',
    codeBlockHint: 'Hand-written code: you can move or delete it, its content is edited in the code',
    fieldGroup: 'Field',
    controlGroup: 'Control',
    componentGroup: 'Properties',
    fieldName: 'Field name',
    fieldNameInvalid: 'Starts with a letter, letters and digits only, not used by another field',
    label: 'Label',
    description: 'Description',
    required: 'Required',
    control: 'Control',
    content: 'Content',
    maxLength: 'Max length',
    min: 'Min',
    max: 'Max',
    integer: 'Whole numbers only',
    scale: 'Decimal places',
    multiple: 'Multiple',
    options: 'Options',
    optionsHint: 'One per line, as value=label',
    maxPeople: 'At most',
    visibleWhen: 'Visible when',
    requiredWhen: 'Required when',
    fieldNotLiteral: 'This field is not written as a literal; change it in the code',
    unknownField: (name) => `The field list has no field ${name}`,
    otherProps: (n) => `${n} more propert${n === 1 ? 'y is' : 'ies are'} set in the code`,
    codeValue: 'Code',
    dropHint: 'Drop here',
  },
};

declare module '@zerodep-dev/vue-tsx-pro-ui' {
  interface LangMessages {
    flowFormDesigner: FlowFormDesignerMessages;
  }
}

/** 当前语言下表单设计器的文案 */
export const useFormDesignerMessages = () => useMessages('flowFormDesigner', flowFormDesignerMessages);
