/**
 * 设计器右边的连线属性（0005 第 6 节）：起点按结果分出边时选哪个结果走这条（自动节点、自定义节点的结果取注册时声明的），
 * 网关出去的线写条件（组件库的 ConditionBuilder，字段取表单的字段清单、流程变量与注册的计算值 fn.名字）、设默认出边，
 * 退回、提级，线上的文字。走线时改流程变量（effects）在 JSON 里写。
 */
import { GATEWAY_TYPES, isBranching, type Condition, type FormManifest, type ProcessDefinition, type ProcessEdge } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { ConditionBuilder, Field, Flex, Select, Small, Switch, TextField, type ConditionField, type ConditionFieldType } from '@zerodep-dev/vue-tsx-pro-ui';
import type { ExtensionCatalog, ExtensionEntry } from '../../client/types';
import { useDesignerMessages } from '../../lang/process-designer-messages';
import { outcomeKeys } from './editing';
import { declaredOutcomes } from './extensions';
import { compact, FieldGroup, optionsOf } from './fields';

/** EdgePanel 的属性 */
export interface EdgePanelProps {
  edge: ProcessEdge;
  definition: ProcessDefinition;
  manifest: FormManifest | null;
  /** 接入方注册的扩展；还没读到时为 null */
  catalog: ExtensionCatalog | null;
  onChange: (next: ProcessEdge, merge?: string) => void;
}

/** 表单字段的类型对到条件构造器的类型；人员、附件、明细表不能直接比较 */
const CONDITION_TYPES: Readonly<Record<string, ConditionFieldType>> = {
  text: 'text',
  number: 'number',
  decimal: 'number',
  boolean: 'boolean',
  date: 'date',
  datetime: 'date',
  select: 'select',
};

/** 条件里能用的表单字段（form.名字）；group 是条件构造器里的分组名，不给时不分组 */
export function formConditionFields(manifest: FormManifest | null, group?: string): ConditionField[] {
  return Object.entries(manifest ?? {}).flatMap(([name, field]): ConditionField[] => {
    const type = CONDITION_TYPES[field.type];
    if (!type) return [];
    const options = Array.isArray(field.options) ? field.options.map((o) => ({ value: o.value, label: o.label })) : undefined;
    return [{ name: `form.${name}`, label: field.label ?? name, type, ...(group ? { group } : {}), ...(options ? { options } : {}) }];
  });
}

/** 条件里能用的字段：表单的字段（form.名字）、流程变量，与接入方注册的计算值（fn.名字，按它声明的类型比较，没声明时当文字） */
export function conditionFields(
  definition: ProcessDefinition,
  manifest: FormManifest | null,
  formGroup: string,
  variableLabel: (name: string) => string,
  computed: readonly ExtensionEntry[] = [],
  computedGroup?: string,
): ConditionField[] {
  const fromForm = formConditionFields(manifest, formGroup);
  const variables = (definition.variables ?? []).flatMap((v): ConditionField[] => {
    const type = CONDITION_TYPES[v.type];
    return type ? [{ name: v.name, label: v.label ?? variableLabel(v.name), type }] : [];
  });
  const fromComputed = computed.map((c): ConditionField => ({
    name: `fn.${c.name}`,
    label: c.label,
    type: CONDITION_TYPES[c.valueType ?? 'text'] ?? 'text',
    ...(computedGroup ? { group: computedGroup } : {}),
  }));
  return [...fromForm, ...variables, ...fromComputed];
}

/** 连线属性 */
export const EdgePanel = _component(({ edge, definition, manifest, catalog, onChange }: EdgePanelProps) => {
  const messages = useDesignerMessages();
  const set = (key: keyof ProcessEdge, value: unknown, merge?: string) => onChange(compact({ ...edge, [key]: value }) as ProcessEdge, merge);
  const source = () => definition.nodes.find((n) => n.id === edge.from);
  const nameOf = (id: string) => definition.nodes.find((n) => n.id === id)?.name || id;

  return () => {
    const from = source();
    const declared = from ? declaredOutcomes(catalog, from) : [];
    const outcomes = from ? [...outcomeKeys(from), ...declared.map((o) => o.value)] : [];
    const labelOf = (key: string) =>
      from?.type === 'task' ? (from.outcomes.find((o) => o.key === key)?.label ?? key) : (declared.find((o) => o.value === key)?.label ?? key);
    const fromGateway = from !== undefined && GATEWAY_TYPES.has(from.type);
    return (
      <Flex direction="column" gap="md">
        <Small>{messages.edgeEnds(nameOf(edge.from), nameOf(edge.to))}</Small>
        <Field label={messages.id}>
          <TextField _value={edge.id} readonly />
        </Field>
        {/* 改接终点：不用在画布上拖线的端点（汇合时把一支改连到汇合的网关） */}
        <Field label={messages.edgeTarget}>
          <Select
            items={definition.nodes.filter((n) => n.type !== 'start' && n.id !== edge.from).map((n) => ({ value: n.id, label: n.name || n.id }))}
            _value={edge.to}
            onUpdate:_value={(to: string | null) => to && set('to', to)}
          />
        </Field>
        {from && isBranching(from.type) && outcomes.length > 0 && (
          <Field label={messages.edgeOutcome}>
            <Select
              items={[{ value: 'any', label: messages.anyOutcome }, ...outcomes.map((key) => ({ value: key, label: labelOf(key), description: key }))]}
              _value={edge.outcome ?? 'any'}
              onUpdate:_value={(key: string | null) => set('outcome', key === 'any' ? undefined : (key ?? undefined))}
            />
          </Field>
        )}
        {fromGateway && from.type !== 'parallel' && (
          <Switch _checked={edge.default === true} onUpdate:_checked={(on) => onChange(on ? withDefault(edge) : without(edge, 'default'))}>
            {messages.isDefault}
          </Switch>
        )}
        {fromGateway && from.type !== 'parallel' && !edge.default && (
          <FieldGroup label={messages.condition}>
            <ConditionBuilder
              fields={conditionFields(definition, manifest, messages.form, messages.variable, catalog?.computed, messages.computed)}
              _value={(edge.when as Condition | undefined) ?? null}
              onUpdate:_value={(when) => set('when', when ?? undefined)}
            />
          </FieldGroup>
        )}
        <Field label={messages.edgeKind}>
          <Select items={optionsOf(messages.edgeKinds)} _value={edge.kind ?? 'none'} onUpdate:_value={(kind: string | null) => set('kind', kind === 'none' ? undefined : (kind ?? undefined))} />
        </Field>
        <Field label={messages.edgeLabel}>
          <TextField _value={edge.label ?? ''} onUpdate:_value={(label) => set('label', label || undefined, `${edge.id}:label`)} />
        </Field>
      </Flex>
    );
  };
});

/** 设成默认出边：默认出边不带条件 */
const withDefault = (edge: ProcessEdge): ProcessEdge => ({ ...without(edge, 'when'), default: true });

/** 去掉连线的一个属性 */
const without = (edge: ProcessEdge, key: 'when' | 'default'): ProcessEdge =>
  Object.fromEntries(Object.entries(edge).filter(([k]) => k !== key)) as unknown as ProcessEdge;
