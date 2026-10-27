/**
 * 表单设计器右边的属性（0004 的 2.3）：选中字段时分两组——字段（标签、说明、必填、长度、范围、选项、显示与必填条件）改清单，
 * 控件（换控件、占位文字……）改 JSX 上的属性；选中布局、展示组件时是它的属性与文字内容；代码块只能移动、删除。
 * 面板不认识的属性原样留着，显示"代码里还设置了 n 个属性"；属性是表达式时显示成代码，只能改那一小段代码。
 */
import type { Condition, FieldDefinition, FieldOption, FormManifest } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import {
  ConditionBuilder,
  Field,
  Flex,
  NumberField,
  Select,
  Small,
  Switch,
  Text,
  Textarea,
  TextField,
} from '@zerodep-dev/vue-tsx-pro-ui';
import type { VNodeChild } from 'vue';
import {
  controlsFor,
  ensureImport,
  renameField,
  replaceTag,
  setField,
  setProp,
  setText,
  type DesignNode,
  type ElementInfo,
  type FormComponent,
  type FormComponentProp,
  type FormDesign,
  type PropValue,
  type TextEdit,
} from '../../form-tools';
import { useFormDesignerMessages } from '../../lang/form-designer-messages';
import { formConditionFields } from '../process-designer/EdgePanel';
import { compact, FieldGroup } from '../process-designer/fields';

/** PropertyPanel 的属性 */
export interface PropertyPanelProps {
  source: string;
  design: FormDesign;
  node: DesignNode;
  /** 改源码；merge 为 true 时与紧挨着的上一次修改合成一步撤销（逐字输入） */
  onEdit: (edits: readonly TextEdit[], merge?: boolean) => void;
}

/** 字段名的规则（0004 的 1.3） */
const FIELD_NAME = /^[a-z][A-Za-z0-9]{0,63}$/;

/** 选中节点的属性 */
export const PropertyPanel = _component(({ source, design, node, onEdit }: PropertyPanelProps) => {
  const messages = useFormDesignerMessages();

  /** 一个元素上的属性：按组件登记的属性说明列出来 */
  const propEditors = (element: ElementInfo, component: FormComponent | null): VNodeChild => {
    const known = component?.props ?? [];
    const others = element.props.filter((p) => p.name !== 'name' && !known.some((k) => k.name === p.name)).length;
    return (
      <>
        {known.map((prop) => (
          <PropEditorField key={prop.name} prop={prop} value={element.props.find((p) => p.name === prop.name)?.value} onChange={(value, merge) => onEdit(setProp(source, element, prop.name, value), merge)} />
        ))}
        {component?.content !== undefined && element.text !== null && (
          <Field label={messages.content}>
            <TextField _value={element.text} onUpdate:_value={(text: string) => onEdit(setText(element, text), true)} />
          </Field>
        )}
        {others > 0 && <Small tone="muted">{messages.otherProps(others)}</Small>}
      </>
    );
  };

  /** 换控件：标签换掉、属性留着，补上导入 */
  const switchControl = (control: ElementInfo, next: FormComponent) => {
    const imported = ensureImport(source, design, next.module, next.name);
    onEdit([...imported.edits, ...replaceTag(source, control, imported.local)]);
  };

  return () => {
    if (node.kind === 'code') {
      return <Text tone="muted">{messages.codeBlockHint}</Text>;
    }
    if (node.kind !== 'field') {
      return <Flex direction="column" gap="md">{node.element && propEditors(node.element, node.component)}</Flex>;
    }
    const name = node.field!.name;
    const entry = design.fields.find((f) => f.name === name);
    const control = node.field!.control;
    const definition = entry?.definition ?? null;
    return (
      <Flex direction="column" gap="lg">
        <FieldGroup label={messages.fieldGroup}>
          {!entry ? (
            <Text tone="danger">{messages.unknownField(name)}</Text>
          ) : !definition ? (
            <Text tone="muted">{messages.fieldNotLiteral}</Text>
          ) : (
            <FieldEditor
              key={name}
              name={name}
              definition={definition}
              manifest={manifestOf(design)}
              onRename={(next) => onEdit(renameField(source, design, name, next))}
              onChange={(next, merge) => onEdit(setField(source, design, name, next), merge)}
            />
          )}
        </FieldGroup>
        {control && (
          <FieldGroup label={messages.controlGroup}>
            {definition && (
              <Field label={messages.control}>
                <Select
                  items={controlsFor(definition).map((c) => ({ value: `${c.module}#${c.name}`, label: c.label }))}
                  _value={node.component ? `${node.component.module}#${node.component.name}` : null}
                  onUpdate:_value={(key: string | null) => {
                    const next = controlsFor(definition).find((c) => `${c.module}#${c.name}` === key);
                    if (next) switchControl(control, next);
                  }}
                />
              </Field>
            )}
            {propEditors(control, node.component)}
          </FieldGroup>
        )}
      </Flex>
    );
  };
});

/** 清单（只含能读出的字段）：条件构造器列字段用 */
const manifestOf = (design: FormDesign): FormManifest =>
  Object.fromEntries(design.fields.flatMap((f) => (f.definition ? [[f.name, f.definition]] : [])));

// ───────── 一个属性 ─────────

interface PropEditorFieldProps {
  prop: FormComponentProp;
  value: PropValue | undefined;
  onChange: (value: PropValue | undefined, merge?: boolean) => void;
}

/** 按登记的编辑方式改一个属性；写成表达式的显示成代码 */
const PropEditorField = _component(({ prop, value, onChange }: PropEditorFieldProps) => {
  const messages = useFormDesignerMessages();
  return () => {
    if (value && 'code' in value) {
      return (
        <Field label={`${prop.label}（${messages.codeValue}）`}>
          <TextField _value={value.code} onUpdate:_value={(code: string) => onChange(code.trim() ? { code } : undefined, true)} />
        </Field>
      );
    }
    const current = value?.literal;
    const editor = prop.editor;
    switch (editor.kind) {
      case 'boolean':
        return <Switch _checked={current === true} onUpdate:_checked={(on: boolean) => onChange(on ? { literal: true } : undefined)}>{prop.label}</Switch>;
      case 'number':
        return (
          <Field label={prop.label}>
            <NumberField _value={typeof current === 'number' ? current : null} onUpdate:_value={(n: number | null) => onChange(n == null ? undefined : { literal: n })} />
          </Field>
        );
      case 'select':
        return (
          <Field label={prop.label}>
            <Select
              items={editor.options.map((option) => ({ value: option, label: option }))}
              clearable
              _value={typeof current === 'string' ? current : null}
              onUpdate:_value={(option: string | null) => onChange(option == null ? undefined : { literal: option })}
            />
          </Field>
        );
      default:
        return (
          <Field label={prop.label}>
            <TextField _value={typeof current === 'string' ? current : ''} onUpdate:_value={(text: string) => onChange(text ? { literal: text } : undefined, true)} />
          </Field>
        );
    }
  };
});

// ───────── 字段的定义 ─────────

interface FieldEditorProps {
  name: string;
  definition: FieldDefinition;
  manifest: FormManifest;
  onRename: (next: string) => void;
  onChange: (next: FieldDefinition, merge?: boolean) => void;
}

/** 字段的属性：改的是清单里的这一项 */
const FieldEditor = _component(({ name, definition, manifest, onRename, onChange }: FieldEditorProps) => {
  const messages = useFormDesignerMessages();
  // 字段名、选项打完再写回：打到一半的名字不合规则，选项打到一半的行也读不对
  let draftName = _state(name);
  let draftOptions = _state(optionsText(definition.options));

  const set = (patch: Partial<Record<string, unknown>>, merge?: boolean) => onChange(compact({ ...definition, ...patch }) as FieldDefinition, merge);
  const nameValid = () => FIELD_NAME.test(draftName) && (draftName === name || !(draftName in manifest));
  const commitName = () => {
    if (draftName !== name && nameValid()) onRename(draftName);
  };
  const commitOptions = () => set({ options: parseOptions(draftOptions, definition.options) });
  const others = () => Object.fromEntries(Object.entries(manifest).filter(([key]) => key !== name));

  const number = (key: keyof FieldDefinition, label: string) => (
    <Field label={label}>
      <NumberField
        _value={typeof definition[key] === 'number' ? (definition[key] as number) : null}
        onUpdate:_value={(n: number | null) => set({ [key]: n ?? undefined })}
      />
    </Field>
  );
  // decimal、日期的范围在清单里写成文字
  const text = (key: keyof FieldDefinition, label: string) => (
    <Field label={label}>
      <TextField _value={typeof definition[key] === 'string' ? (definition[key] as string) : ''} onUpdate:_value={(v: string) => set({ [key]: v || undefined }, true)} />
    </Field>
  );

  return () => (
    <Flex direction="column" gap="md">
      <Field label={messages.fieldName} {...(nameValid() ? {} : { error: messages.fieldNameInvalid })}>
        <TextField _value={draftName} onChange={commitName} onKeydown={(e: KeyboardEvent) => e.key === 'Enter' && commitName()} />
      </Field>
      <Field label={messages.label}>
        <TextField _value={definition.label} onUpdate:_value={(label: string) => set({ label }, true)} />
      </Field>
      <Field label={messages.description}>
        <TextField _value={definition.description ?? ''} onUpdate:_value={(v: string) => set({ description: v || undefined }, true)} />
      </Field>
      <Switch _checked={definition.required === true} onUpdate:_checked={(on: boolean) => set({ required: on || undefined })}>
        {messages.required}
      </Switch>
      {definition.type === 'text' && number('maxLength', messages.maxLength)}
      {definition.type === 'number' && (
        <>
          {number('min', messages.min)}
          {number('max', messages.max)}
          <Switch _checked={definition.integer === true} onUpdate:_checked={(on: boolean) => set({ integer: on || undefined })}>
            {messages.integer}
          </Switch>
        </>
      )}
      {definition.type === 'decimal' && (
        <>
          {number('scale', messages.scale)}
          {text('min', messages.min)}
          {text('max', messages.max)}
        </>
      )}
      {(definition.type === 'date' || definition.type === 'datetime') && (
        <>
          {text('min', messages.min)}
          {text('max', messages.max)}
        </>
      )}
      {definition.type === 'select' && (
        <>
          <Field label={messages.options} description={messages.optionsHint}>
            <Textarea _value={draftOptions} rows={4} onChange={commitOptions} />
          </Field>
          <Switch _checked={definition.multiple === true} onUpdate:_checked={(on: boolean) => set({ multiple: on || undefined })}>
            {messages.multiple}
          </Switch>
        </>
      )}
      {definition.type === 'people' && number('max', messages.maxPeople)}
      <FieldGroup label={messages.visibleWhen}>
        <ConditionBuilder
          fields={formConditionFields(others())}
          _value={(definition.visibleWhen as Condition | undefined) ?? null}
          onUpdate:_value={(when: Condition | null) => set({ visibleWhen: when ?? undefined })}
        />
      </FieldGroup>
      <FieldGroup label={messages.requiredWhen}>
        <ConditionBuilder
          fields={formConditionFields(others())}
          _value={(definition.requiredWhen as Condition | undefined) ?? null}
          onUpdate:_value={(when: Condition | null) => set({ requiredWhen: when ?? undefined })}
        />
      </FieldGroup>
    </Flex>
  );
});

/** 选项写成一行一个"值=名字" */
function optionsText(options: FieldDefinition['options']): string {
  return Array.isArray(options) ? options.map((o) => `${o.value}=${o.label}`).join('\n') : '';
}

/** 读回选项：原来的值都是数时，像数的值仍按数存 */
function parseOptions(text: string, before: FieldDefinition['options']): FieldOption[] {
  const numeric = Array.isArray(before) && before.length > 0 && before.every((o) => typeof o.value === 'number');
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const at = line.indexOf('=');
      const raw = at < 0 ? line : line.slice(0, at).trim();
      const label = at < 0 ? line : line.slice(at + 1).trim();
      return { value: numeric && /^-?\d+(\.\d+)?$/.test(raw) ? Number(raw) : raw, label };
    });
}
