/**
 * form.Field 里的控件按字段类型补上没写的属性，表单代码只写 `<form.Field name="amount"><NumberField /></form.Field>` 就能对上强类型的值：
 *
 *   NumberField    number：步长（integer 时 1，否则足够细，失焦时不会被对齐成整数）、min、max
 *                  decimal：值按 Decimal 读写、步长与显示的小数位按 scale
 *   DatePicker、DateField   值是 CalendarDate / ZonedDateTime（valueType="dateValue"）；datetime 到分钟，新选的本地时间按浏览器的时区换成时刻
 *   Select         选项取清单的 options、多选按 multiple；选项的值可以是数字，控件按文字选，来回换
 *   PeoplePicker   人员数据源取容器给的 people，types、max 取清单
 *   TextField、Textarea     maxlength 取清单
 *
 * 只补没写的：代码里写了的属性原样用。按组件本身认（vnode.type），不按标签名。
 */
import type { FieldDefinition, FieldOption } from '@zerodep-dev/flow-model';
import {
  DateField,
  DatePicker,
  NumberField,
  PeoplePicker,
  Select,
  Textarea,
  TextField,
  type PeopleSource,
} from '@zerodep-dev/vue-tsx-pro-ui';
import { CalendarDateTime, getLocalTimeZone, toZoned } from '@internationalized/date';
import Decimal from 'decimal.js';
import { cloneVNode, isVNode, type VNode, type VNodeChild } from 'vue';

/** 控件绑定的字段：读写它的值、人员数据源 */
export interface ControlBinding {
  get(): unknown;
  set(value: unknown): void;
  people?: PeopleSource;
}

/** 给 form.Field 的内容补属性；不是单个控件、没有字段定义时原样返回 */
export function bindControl(children: unknown, field: FieldDefinition | undefined, binding: ControlBinding): VNodeChild {
  // 组件库的 Field 认出"只有一个控件"才自动绑定值，包在数组里认不出：拿出这一个
  const only = Array.isArray(children) && children.length === 1 ? children[0] : children;
  if (!isVNode(only) || !field) return children as VNodeChild;
  const extra = extraProps(only, field, binding);
  return Object.keys(extra).length ? cloneVNode(only, extra) : only;
}

function extraProps(control: VNode, field: FieldDefinition, binding: ControlBinding): Record<string, unknown> {
  const written = control.props ?? {};
  const out: Record<string, unknown> = {};
  const put = (name: string, value: unknown) => {
    if (value !== undefined && !(name in written) && !(kebab(name) in written)) out[name] = value;
  };
  const bindValue = (read: () => unknown, write: (value: never) => void) => {
    if ('_value' in written) return;
    out._value = read();
    out['onUpdate:_value'] = write;
  };

  switch (control.type) {
    case NumberField:
      if (field.type === 'decimal') {
        put('valueType', Decimal);
        put('step', new Decimal(field.scale === undefined ? '1e-20' : `1e-${field.scale}`));
        if (field.scale !== undefined) put('format', { minimumFractionDigits: field.scale, maximumFractionDigits: field.scale });
      } else {
        put('step', field.integer ? 1 : 0.000001);
        put('min', numberOf(field.min));
        put('max', numberOf(field.max));
      }
      break;
    case DatePicker:
    case DateField:
      put('valueType', 'dateValue');
      put('min', textOf(field.min));
      put('max', textOf(field.max));
      if (field.type === 'datetime') {
        put('granularity', 'minute');
        // 值是 ZonedDateTime 时控件照样写回它；从空值新选出的是本地的 CalendarDateTime
        bindValue(binding.get, (value: unknown) =>
          binding.set(value instanceof CalendarDateTime ? toZoned(value, getLocalTimeZone()) : value),
        );
      }
      break;
    case Select: {
      const options: readonly FieldOption[] = Array.isArray(field.options) ? field.options : [];
      const original = (text: string) => options.find((option) => String(option.value) === text)?.value ?? text;
      put('items', options.map((option) => ({ value: String(option.value), label: option.label })));
      if (field.multiple) {
        put('selectionMode', 'multiple');
        bindValue(
          () => ((binding.get() ?? []) as unknown[]).map(String),
          (texts: string[]) => binding.set(texts.map(original)),
        );
      } else {
        put('clearable', !field.required);
        bindValue(
          () => (binding.get() == null ? null : String(binding.get())),
          (text: string | null) => binding.set(text == null ? null : original(text)),
        );
      }
      break;
    }
    case PeoplePicker:
      put('source', binding.people);
      put('types', field.types);
      put('max', numberOf(field.max));
      break;
    case TextField:
    case Textarea:
      put('maxlength', field.maxLength);
      break;
  }
  return out;
}

const kebab = (name: string) => name.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`);
const numberOf = (value: unknown) => (typeof value === 'number' ? value : undefined);
const textOf = (value: unknown) => (typeof value === 'string' ? value : undefined);
