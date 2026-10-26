/**
 * 按字段清单自动排版的表单：表单还没有代码（只发布了清单）时容器用它。每个字段一个控件，按清单的先后从上往下排：
 *
 *   text      TextField；maxLength 超过 100 时 Textarea
 *   number    NumberField              decimal   NumberField（按 Decimal 读写）
 *   boolean   Switch                   date      DatePicker              datetime  DatePicker（到分钟，按本地时间填、按 UTC 存）
 *   select    Select（多选时 multiple） people    PeoplePicker（容器给了人员数据源时；没给时只显示名字）
 *   file      只显示文件名（上传由接入方的表单代码做）
 *   list      明细表：每行一组列，能改时可以加行、删行
 *
 * 想要别的排版、联动、远程数据时写代码表单（defineFlowForm）。
 */
import type { FieldDefinition, FieldOption, FormManifest } from '@zerodep-dev/flow-model';
import { _component, type TsxComponent } from '@zerodep-dev/vue-tsx-pro-tsx';
import {
  Button,
  DatePicker,
  Flex,
  FormSection,
  NumberField,
  PeoplePicker,
  Select,
  Switch,
  Text,
  Textarea,
  TextField,
  type PeopleEntry,
} from '@zerodep-dev/vue-tsx-pro-ui';
import { Plus, Trash2 } from '@lucide/icons';
import type { VNodeChild } from 'vue';
import { useFlowMessages, type FlowMessages } from '../lang/messages';
import { defineFlowForm, type FlowFormApi, type FlowFormComponentProps } from './define';
import type { FlowFormScope } from './context';

/** 同一份清单只建一次组件（容器每次渲染都会要） */
const cache = new WeakMap<FormManifest, TsxComponent<FlowFormComponentProps>>();

/** 按清单自动排版的表单组件 */
export function autoFlowForm(manifest: FormManifest): TsxComponent<FlowFormComponentProps> {
  let component = cache.get(manifest);
  if (!component) {
    component = defineFlowForm(manifest, ({ form, context }) => {
      const messages = useFlowMessages();
      return () => (
        <form.Form>
          <Flex direction="column" gap="md">
            {Object.entries(manifest).map(([name, field]) => fieldOf(form, context, messages, name, field))}
          </Flex>
        </form.Form>
      );
    });
    cache.set(manifest, component);
  }
  return component;
}

/** 一个字段：标签、必填、只读、隐藏由 form.Field 按权限给，这里只挑控件 */
function fieldOf(
  form: FlowFormApi<FormManifest>,
  context: FlowFormScope,
  messages: FlowMessages,
  name: string,
  field: FieldDefinition,
): VNodeChild {
  if (field.type === 'list') return listOf(form, context, messages, name, field);
  return (
    <form.Field key={name} name={name}>
      {controlOf(form, context, name, field)}
    </form.Field>
  );
}

/** 按字段类型挑控件；值由 form.Field 自动绑定（只有一个控件时） */
function controlOf(form: FlowFormApi<FormManifest>, context: FlowFormScope, path: string, field: FieldDefinition): VNodeChild {
  switch (field.type) {
    case 'number':
      return <NumberField min={numberOf(field.min)} max={numberOf(field.max)} step={field.integer ? 1 : undefined} />;
    case 'decimal':
      // 按清单的小数位显示（12,000.00），输入时同样限到这么多位
      return field.scale === undefined ? (
        <NumberField />
      ) : (
        <NumberField format={{ minimumFractionDigits: field.scale, maximumFractionDigits: field.scale }} />
      );
    case 'boolean':
      return <Switch />;
    case 'date':
      return <DatePicker min={textOf(field.min)} max={textOf(field.max)} />;
    case 'datetime':
      return (
        <DatePicker
          granularity="minute"
          _value={localMinute(form.getValue(path) as string | null)}
          onUpdate:_value={(local: string | null) => form.setValue(path, local ? new Date(local).toISOString() : null)}
        />
      );
    case 'select':
      return selectOf(form, path, field);
    case 'people':
      return context.people ? (
        <PeoplePicker source={context.people} types={field.types as never} max={numberOf(field.max)} />
      ) : (
        <Text>{((form.getValue(path) ?? []) as PeopleEntry[]).map((p) => p.name).join('、')}</Text>
      );
    case 'file':
      return <Text>{((form.getValue(path) ?? []) as { name: string }[]).map((f) => f.name).join('、')}</Text>;
    default:
      return (field.maxLength ?? 0) > 100 ? <Textarea maxlength={field.maxLength} /> : <TextField maxlength={field.maxLength} />;
  }
}

/** 明细表：每行一组列；能改时可以加行、删行 */
function listOf(
  form: FlowFormApi<FormManifest>,
  context: FlowFormScope,
  messages: FlowMessages,
  name: string,
  field: FieldDefinition,
): VNodeChild {
  const state = context.access(name);
  if (!state.visible) return null;
  const columns = Object.entries(field.fields ?? {});
  const rows = form.array(name as never);
  const editable = state.editable && context.mode === 'fill';
  const blankRow = () => Object.fromEntries(columns.map(([column, definition]) => [column, definition.default ?? null]));
  return (
    <FormSection key={name} title={field.label} description={field.description}>
      {rows.items.map((_, index) => (
        <Flex key={rows.keys[index]} gap="md" align="end" wrap>
          {columns.map(([column, definition]) => (
            <form.Field key={column} name={`${name}.${index}.${column}`}>
              {controlOf(form, context, `${name}.${index}.${column}`, definition)}
            </form.Field>
          ))}
          {editable && (
            <Button variant="ghost" icon={Trash2} aria-label={messages.removeRow(index + 1)} onClick={() => rows.remove(index)} />
          )}
        </Flex>
      ))}
      {editable && (
        <Button variant="outline" icon={Plus} onClick={() => rows.append(blankRow() as never)}>
          {messages.addRow(field.label)}
        </Button>
      )}
    </FormSection>
  );
}

/**
 * 下拉选择：选项的值可以是数字，组件库的 Select 按文字选，这里来回换（值按清单里选项本来的类型存）。
 * 选项来自字典（{ source }）时还没有接，列表是空的
 */
function selectOf(form: FlowFormApi<FormManifest>, path: string, field: FieldDefinition): VNodeChild {
  const options: readonly FieldOption[] = Array.isArray(field.options) ? field.options : [];
  const items = options.map((option) => ({ value: String(option.value), label: option.label }));
  const original = (text: string) => options.find((option) => String(option.value) === text)?.value ?? text;
  const value = form.getValue(path);
  if (field.multiple) {
    return (
      <Select
        items={items}
        selectionMode="multiple"
        _value={((value ?? []) as unknown[]).map(String)}
        onUpdate:_value={(texts: string[]) => form.setValue(path, texts.map(original))}
      />
    );
  }
  return (
    <Select
      items={items}
      clearable={!field.required}
      _value={value == null ? null : String(value)}
      onUpdate:_value={(text: string | null) => form.setValue(path, text == null ? null : original(text))}
    />
  );
}

const numberOf = (value: unknown) => (typeof value === 'number' ? value : undefined);
const textOf = (value: unknown) => (typeof value === 'string' ? value : undefined);

/** UTC 的时刻 → 本地的“年-月-日T时:分”（DatePicker 到分钟时的值） */
function localMinute(iso: string | null): string | null {
  if (!iso) return null;
  const at = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${at.getFullYear()}-${pad(at.getMonth() + 1)}-${pad(at.getDate())}T${pad(at.getHours())}:${pad(at.getMinutes())}`;
}
