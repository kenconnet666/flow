/**
 * 表单设计器认得的组件（0004 的 2.1、2.4）：面板里能拖的、画布上能选中改属性的。按"从哪个模块导入的哪个名字"认，
 * 不按标签名的文字，`import { TextField as Input }` 也认得。没登记的元素在设计器里是代码块。
 *
 *   registerFormComponent({
 *     name: 'SupplierPicker', module: '@host/supplier', label: '供应商', group: '业务',
 *     field: ['select'], props: [{ name: 'scope', label: '范围', editor: { kind: 'select', options: ['active', 'all'] } }],
 *   });
 */
import type { FieldDefinition, FieldType } from '@zerodep-dev/flow-model';

/** 属性面板里怎么改一个属性 */
export type PropEditor =
  | { readonly kind: 'text' }
  | { readonly kind: 'number' }
  | { readonly kind: 'boolean' }
  | { readonly kind: 'select'; readonly options: readonly string[] };

/** 属性面板里列出的一个属性 */
export interface FormComponentProp {
  readonly name: string;
  readonly label: string;
  readonly editor: PropEditor;
}

/** 一个登记的组件 */
export interface FormComponent {
  /** 模块导出的名字 */
  readonly name: string;
  /** 从哪个模块导入（模块表里的名字） */
  readonly module: string;
  /** 面板里显示的名字 */
  readonly label: string;
  /** 面板里的分类：layout、display、field，或接入方自己的分类名 */
  readonly group: string;
  /** 能往里面放别的组件 */
  readonly container?: boolean;
  /** 能当哪些类型字段的控件；不写就不是字段控件 */
  readonly field?: readonly FieldType[];
  /** 只适合某些字段（Rating 只限整数） */
  readonly fits?: (field: FieldDefinition) => boolean;
  /** 属性面板里列出的常见属性 */
  readonly props?: readonly FormComponentProp[];
  /** 新放进来时写上的属性 */
  readonly defaults?: Readonly<Record<string, string | number | boolean>>;
  /** 有文字内容（Text、Alert）：新放进来时的内容，属性面板里能改 */
  readonly content?: string;
}

/** 组件库 */
export const UI_MODULE = '@zerodep-dev/vue-tsx-pro-ui';
/** 表单写法（defineFields、defineFlowForm） */
export const FORM_MODULE = '@zerodep-dev/flow-ui/form';

const text = { kind: 'text' } as const;
const number = { kind: 'number' } as const;
const boolean = { kind: 'boolean' } as const;
const choice = (...options: string[]) => ({ kind: 'select', options }) as const;
const SPACES = choice('none', 'xs', 'sm', 'md', 'lg', 'xl');
const placeholder: FormComponentProp = { name: 'placeholder', label: '占位文字', editor: text };

const BUILTIN: readonly FormComponent[] = [
  {
    name: 'FormSection', module: UI_MODULE, label: '分组', group: 'layout', container: true, defaults: { title: '分组' },
    props: [
      { name: 'title', label: '标题', editor: text },
      { name: 'description', label: '说明', editor: text },
      { name: 'columns', label: '列数', editor: number },
    ],
  },
  {
    name: 'Grid', module: UI_MODULE, label: '栅格', group: 'layout', container: true, defaults: { columns: 2, gap: 'md' },
    props: [
      { name: 'columns', label: '列数', editor: number },
      { name: 'gap', label: '间距', editor: SPACES },
    ],
  },
  {
    name: 'GridItem', module: UI_MODULE, label: '栅格项', group: 'layout', container: true,
    props: [{ name: 'span', label: '跨列', editor: number }],
  },
  {
    name: 'Flex', module: UI_MODULE, label: '排列', group: 'layout', container: true, defaults: { direction: 'column', gap: 'md' },
    props: [
      { name: 'direction', label: '方向', editor: choice('row', 'column') },
      { name: 'gap', label: '间距', editor: SPACES },
      { name: 'wrap', label: '换行', editor: boolean },
    ],
  },
  { name: 'Separator', module: UI_MODULE, label: '分割线', group: 'layout' },
  {
    name: 'Text', module: UI_MODULE, label: '文字', group: 'display', content: '说明文字',
    props: [
      { name: 'tone', label: '色调', editor: choice('neutral', 'muted', 'accent', 'info', 'success', 'warning', 'danger') },
      { name: 'size', label: '字号', editor: choice('xs', 'sm', 'md', 'lg', 'xl') },
    ],
  },
  {
    name: 'Alert', module: UI_MODULE, label: '提示', group: 'display', content: '提示内容',
    props: [
      { name: 'tone', label: '色调', editor: choice('neutral', 'accent', 'info', 'success', 'warning', 'danger') },
      { name: 'title', label: '标题', editor: text },
    ],
  },
  { name: 'TextField', module: UI_MODULE, label: '单行文字', group: 'field', field: ['text'], props: [placeholder] },
  {
    name: 'Textarea', module: UI_MODULE, label: '多行文字', group: 'field', field: ['text'],
    props: [placeholder, { name: 'rows', label: '行数', editor: number }],
  },
  { name: 'NumberField', module: UI_MODULE, label: '数字', group: 'field', field: ['number', 'decimal'], props: [placeholder] },
  { name: 'Slider', module: UI_MODULE, label: '滑块', group: 'field', field: ['number'] },
  {
    name: 'Rating', module: UI_MODULE, label: '评分', group: 'field', field: ['number'], fits: (field) => field.integer === true,
    props: [{ name: 'count', label: '星数', editor: number }],
  },
  { name: 'Switch', module: UI_MODULE, label: '开关', group: 'field', field: ['boolean'] },
  { name: 'Checkbox', module: UI_MODULE, label: '勾选', group: 'field', field: ['boolean'] },
  { name: 'DatePicker', module: UI_MODULE, label: '日期选择', group: 'field', field: ['date', 'datetime'], props: [placeholder] },
  { name: 'DateField', module: UI_MODULE, label: '日期输入', group: 'field', field: ['date'] },
  { name: 'Select', module: UI_MODULE, label: '下拉选择', group: 'field', field: ['select'], props: [placeholder] },
  {
    name: 'RadioGroup', module: UI_MODULE, label: '单选组', group: 'field', field: ['select'], fits: (field) => !field.multiple,
    props: [{ name: 'orientation', label: '方向', editor: choice('vertical', 'horizontal') }],
  },
  {
    name: 'CheckboxGroup', module: UI_MODULE, label: '多选组', group: 'field', field: ['select'], fits: (field) => field.multiple === true,
    props: [{ name: 'orientation', label: '方向', editor: choice('vertical', 'horizontal') }],
  },
  { name: 'PeoplePicker', module: UI_MODULE, label: '选人', group: 'field', field: ['people'] },
  // 附件：流程组件库自己的控件（组件库的 Upload 接上接入方的 FileSource）
  { name: 'FileField', module: FORM_MODULE, label: '附件', group: 'field', field: ['file'] },
];

const registered: FormComponent[] = [];

/** 登记一个业务组件（接入方前端启动时，随模块表一起）；同一个模块的同名组件后登记的替换先登记的 */
export function registerFormComponent(component: FormComponent): void {
  const at = registered.findIndex((c) => c.module === component.module && c.name === component.name);
  if (at >= 0) registered.splice(at, 1, component);
  else registered.push(component);
}

/** 全部认得的组件：内置的在前 */
export function formComponents(): readonly FormComponent[] {
  return [...BUILTIN, ...registered];
}

/** 按模块与导出名找组件 */
export function findFormComponent(module: string, name: string): FormComponent | undefined {
  return formComponents().find((c) => c.module === module && c.name === name);
}

/**
 * 接入方登记的组件声明了能当这种字段的控件（`field: ['money']`）：这个类型就是接入方注册的字段类型，
 * 字段清单的校验认得它（后端另有同名的 FieldType 查值）
 */
export function isRegisteredFieldType(type: string): boolean {
  return registered.some((c) => c.field?.includes(type));
}

/** 一个字段能用的控件，第一个是默认的 */
export function controlsFor(field: FieldDefinition): readonly FormComponent[] {
  return formComponents().filter((c) => c.field?.includes(field.type) && (c.fits?.(field) ?? true));
}
