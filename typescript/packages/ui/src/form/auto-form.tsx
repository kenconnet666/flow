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
import type { FieldDefinition, FormManifest } from '@zerodep-dev/flow-model';
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
import { h, type VNodeChild } from 'vue';
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

/** 按字段类型挑控件：值的绑定、步长、选项、人员数据源这些由 form.Field 按字段补上（controls.ts） */
function controlOf(form: FlowFormApi<FormManifest>, context: FlowFormScope, path: string, field: FieldDefinition): VNodeChild {
  switch (field.type) {
    case 'number':
    case 'decimal':
      return <NumberField />;
    case 'boolean':
      return <Switch />;
    case 'date':
    case 'datetime':
      return <DatePicker />;
    case 'select':
      // 选项由 form.Field 按清单补上（Select 的类型要求写 items）
      return h(Select);
    case 'people':
      return context.people ? <PeoplePicker source={context.people} /> : <Text>{((form.getValue(path) ?? []) as PeopleEntry[]).map((p) => p.name).join('、')}</Text>;
    case 'file':
      return <Text>{((form.getValue(path) ?? []) as { name: string }[]).map((f) => f.name).join('、')}</Text>;
    default:
      return (field.maxLength ?? 0) > 100 ? <Textarea /> : <TextField />;
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

