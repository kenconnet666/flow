/**
 * 接入方注册的扩展（后端的扩展目录 `GET /queries/extensions`）在属性面板里的控件：扩展名的下拉、按参数声明画的表单。
 * 解析器、动作、守卫、计算值、自定义节点、节点的业务信息都用这两样，参数写 JSON 只留给没声明参数的扩展。
 */
import { isRef } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Field, Flex, NumberField, Select, Small, Switch, Textarea, TextField } from '@zerodep-dev/vue-tsx-pro-ui';
import type { ProcessNode } from '@zerodep-dev/flow-model';
import type { ExtensionCatalog, ExtensionChoice, ExtensionEntry, ExtensionParam } from '../../client/types';
import { useDesignerMessages } from '../../lang/process-designer-messages';

/** ExtensionSelect 的属性 */
export interface ExtensionSelectProps {
  /** 注册了的这一种扩展 */
  entries: readonly ExtensionEntry[];
  value: string | null;
  onChange: (name: string | null) => void;
  label: string;
  /** 一种也没注册时的占位 */
  placeholder?: string;
}

/** 扩展的名字：列出注册了的（显示名，名字作说明）；定义里写着却没注册的名字也列出来，标"未注册"，不悄悄丢掉 */
export const ExtensionSelect = _component(({ entries, value, onChange, label, placeholder }: ExtensionSelectProps) => {
  const messages = useDesignerMessages();
  const items = () => [
    ...entries.map((e) => ({ value: e.name, label: e.label, description: e.description ?? e.name })),
    ...(value && !entries.some((e) => e.name === value) ? [{ value, label: messages.unregistered(value) }] : []),
  ];
  return () => (
    <Select items={items()} _value={value} searchable clearable placeholder={placeholder} aria-label={label} onUpdate:_value={(name: string | null) => onChange(name)} />
  );
});

/** ParamsForm 的属性 */
export interface ParamsFormProps {
  params: readonly ExtensionParam[];
  value: Readonly<Record<string, unknown>> | undefined;
  /** 改了一个参数；全空时给 undefined（定义里不留空对象）。merge 是逐字输入时合成一步撤销用的键 */
  onChange: (next: Record<string, unknown> | undefined, merge?: string) => void;
  /** 合并撤销的键的前缀：同一个节点上的同一组参数 */
  mergeKey: string;
}

/**
 * 按参数声明画的表单：文字、数字、是否、日期、下拉。值是引用（`{ ref }`）的参数只显示取自哪里，在 JSON 里改；
 * 声明之外的键原样保留。
 */
export const ParamsForm = _component(({ params, value, onChange, mergeKey }: ParamsFormProps) => {
  const messages = useDesignerMessages();
  const write = (key: string, next: unknown, merge?: string) => {
    const merged = Object.fromEntries(Object.entries({ ...value, [key]: next }).filter(([, v]) => v !== undefined && v !== null && v !== ''));
    onChange(Object.keys(merged).length ? merged : undefined, merge && `${mergeKey}:${merge}`);
  };

  const control = (param: ExtensionParam, label: string) => {
    const current = value?.[param.key];
    switch (param.type) {
      case 'number':
      case 'decimal':
        return (
          <NumberField
            _value={typeof current === 'number' ? current : null}
            aria-label={label}
            onUpdate:_value={(n) => write(param.key, n == null ? undefined : Number(n), param.key)}
          />
        );
      case 'boolean':
        return (
<Switch _checked={current === true} aria-label={label} onUpdate:_checked={(on) => write(param.key, on || undefined)} />
        );
      case 'select':
        return (
          <Select
            items={param.options.map((o) => ({ value: o.value, label: o.label }))}
            _value={typeof current === 'string' ? current : null}
            clearable
            aria-label={label}
            onUpdate:_value={(v: string | null) => write(param.key, v ?? undefined)}
          />
        );
      default:
        return (
          <TextField
            _value={typeof current === 'string' ? current : ''}
            placeholder={param.type === 'date' ? 'YYYY-MM-DD' : undefined}
            aria-label={label}
            onUpdate:_value={(text) => write(param.key, text || undefined, param.key)}
          />
        );
    }
  };

  return () => (
    <Flex direction="column" gap="sm">
      {params.map((param) => {
        const label = param.label ?? param.key;
        const current = value?.[param.key];
        const missing = param.required && (current === undefined || current === null || current === '');
        return (
          <Field
            key={param.key}
            label={label}
            description={param.description ?? undefined}
            required={param.required}
            invalid={missing}
            error={missing ? messages.paramRequired : undefined}
          >
            {isRef(current) ? <Small>{messages.refValue(current.ref)}</Small> : control(param, label)}
          </Field>
        );
      })}
    </Flex>
  );
});

/** 自动节点的动作、自定义节点注册时声明的结果（出边按结果走时给连线选）；别的节点、没声明时为空 */
export function declaredOutcomes(catalog: ExtensionCatalog | null, node: ProcessNode): readonly ExtensionChoice[] {
  if (node.type === 'service') return entryOf(catalog?.actions, node.action)?.outcomes ?? [];
  if (node.type.startsWith('x-')) return entryOf(catalog?.nodes, node.type)?.outcomes ?? [];
  return [];
}

/** 扩展目录给校验用：哪个名字注册了（模型包 validateProcess 的 isRegistered） */
export function registeredIn(catalog: ExtensionCatalog) {
  const lists = {
    resolver: catalog.resolvers,
    action: catalog.actions,
    guard: catalog.guards,
    computed: catalog.computed,
    node: catalog.nodes,
    completion: catalog.completions,
  } as const;
  return (kind: keyof typeof lists, name: string) => lists[kind].some((e) => e.name === name);
}

/** 名字对应的扩展；没注册时为 undefined */
export const entryOf = (entries: readonly ExtensionEntry[] | undefined, name: string | undefined) =>
  name ? entries?.find((e) => e.name === name) : undefined;

/** JsonObjectField 的属性 */
export interface JsonObjectFieldProps {
  label: string;
  value: Readonly<Record<string, unknown>> | undefined;
  onChange: (next: Record<string, unknown> | undefined) => void;
}

/** 一个 JSON 对象（没声明参数的扩展的参数）：写成合法的对象才写回，写错时提示、不改 */
export const JsonObjectField = _component(({ label, value, onChange }: JsonObjectFieldProps) => {
  const messages = useDesignerMessages();
  let draft = _state<string | null>(null);
  const text = () => draft ?? (value ? JSON.stringify(value, null, 2) : '');
  const invalid = () => {
    if (draft === null || draft.trim() === '') return false;
    try {
      const parsed: unknown = JSON.parse(draft);
      return typeof parsed !== 'object' || parsed === null || Array.isArray(parsed);
    } catch {
      return true;
    }
  };
  function commit() {
    if (draft === null || invalid()) return;
    onChange(draft.trim() === '' ? undefined : (JSON.parse(draft) as Record<string, unknown>));
    draft = null;
  }
  return () => (
    <Field label={label} invalid={invalid()} error={invalid() ? messages.argsInvalid : undefined}>
      <Textarea _value={text()} rows={3} onUpdate:_value={(next) => (draft = next)} onBlur={commit} />
    </Field>
  );
});
