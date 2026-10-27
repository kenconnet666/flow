/**
 * 明细表的列（字段类型 list 的 fields）：每列一行，改名、改标签、改类型、必填，删列，加列。
 * 改的是清单里这个明细表的定义；列名同字段名的规则（字母开头，字母数字下划线），一张表里不重名。
 */
import type { FieldDefinition, FieldType, FormManifest } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Button, Field, Flex, Select, Switch, TextField } from '@zerodep-dev/vue-tsx-pro-ui';
import { Plus, Trash2 } from '@lucide/icons';
import { useFormDesignerMessages } from '../../lang/form-designer-messages';
import { FieldGroup } from '../process-designer/fields';

/** 列能用的类型：明细表里不再套明细表，人员、附件、选择这些要更多设置的放在单独的字段里 */
const COLUMN_TYPES: readonly FieldType[] = ['text', 'number', 'decimal', 'boolean', 'date'];
const COLUMN_NAME = /^[A-Za-z][A-Za-z0-9_]*$/;

export interface ColumnsEditorProps {
  /** 现在的列 */
  columns: FormManifest;
  /** 改了之后的整组列（保持先后） */
  onChange: (next: FormManifest) => void;
}

/** 明细表的列 */
export const ColumnsEditor = _component(({ columns, onChange }: ColumnsEditorProps) => {
  const messages = useFormDesignerMessages();
  const entries = () => Object.entries(columns);
  const replace = (at: string, name: string, definition: FieldDefinition) =>
    onChange(Object.fromEntries(entries().map(([key, value]) => (key === at ? [name, definition] : [key, value]))));
  const remove = (at: string) => onChange(Object.fromEntries(entries().filter(([key]) => key !== at)));
  const add = () => {
    let n = entries().length + 1;
    while (`col${n}` in columns) n++;
    onChange({ ...columns, [`col${n}`]: { label: messages.newColumnLabel(n), type: 'text' } });
  };
  const types = () => COLUMN_TYPES.map((type) => ({ value: type, label: messages.fieldTypes[type] ?? type }));

  return () => (
    <FieldGroup label={messages.columns}>
      <Flex direction="column" gap="sm">
        {entries().map(([name, definition]) => (
          <ColumnRow
            key={name}
            name={name}
            definition={definition}
            taken={(next) => next !== name && next in columns}
            types={types()}
            onChange={(next, nextDefinition) => replace(name, next, nextDefinition)}
            onRemove={() => remove(name)}
          />
        ))}
        <Button variant="outline" size="sm" icon={Plus} onClick={add}>
          {messages.addColumn}
        </Button>
      </Flex>
    </FieldGroup>
  );
});

interface ColumnRowProps {
  name: string;
  definition: FieldDefinition;
  taken: (name: string) => boolean;
  types: readonly { value: string; label: string }[];
  onChange: (name: string, definition: FieldDefinition) => void;
  onRemove: () => void;
}

/** 一列：列名打完（失焦、回车）才写回，打到一半的名字不合规则 */
const ColumnRow = _component(({ name, definition, taken, types, onChange, onRemove }: ColumnRowProps) => {
  const messages = useFormDesignerMessages();
  let draft = _state(name);
  const valid = () => COLUMN_NAME.test(draft) && !taken(draft);
  const commit = () => {
    if (draft !== name && valid()) onChange(draft, definition);
  };
  const set = (patch: Partial<FieldDefinition>) => onChange(name, { ...definition, ...patch } as FieldDefinition);

  // 每一列一组、以列的标签为名：几列的"字段名""标签"读屏、按名字找时分得清
  return () => (
    <Flex gap="sm" align="end" wrap role="group" aria-label={definition.label}>
      <Field label={messages.fieldName} {...(valid() ? {} : { error: messages.fieldNameInvalid })} style={{ width: '7rem' }}>
        <TextField _value={draft} onChange={commit} onKeydown={(e: KeyboardEvent) => e.key === 'Enter' && commit()} />
      </Field>
      <Field label={messages.label} style={{ width: '8rem' }}>
        <TextField _value={definition.label} onUpdate:_value={(label: string) => set({ label })} />
      </Field>
      <Field label={messages.control} style={{ width: '7rem' }}>
        <Select items={types} _value={definition.type} onUpdate:_value={(type: string | null) => type && set({ type: type as FieldType })} />
      </Field>
      <Switch _checked={definition.required === true} onUpdate:_checked={(on: boolean) => set({ required: on || undefined })}>
        {messages.required}
      </Switch>
      <Button variant="ghost" size="sm" icon={Trash2} aria-label={messages.removeColumn(definition.label)} onClick={onRemove} />
    </Flex>
  );
});
