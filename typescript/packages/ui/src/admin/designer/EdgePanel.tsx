/**
 * 设计器右边的连线属性（0005 第 6 节）：起点按结果分出边时选哪个结果走这条，网关出去的线写条件（组件库的 ConditionBuilder，
 * 字段取表单的字段清单与流程变量）、设默认出边，退回、提级，线上的文字。走线时改流程变量（effects）在 JSON 里写。
 */
import { BRANCHING_TYPES, GATEWAY_TYPES, type Condition, type FormManifest, type ProcessDefinition, type ProcessEdge } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { ConditionBuilder, Field, Flex, Select, Small, Switch, TextField, type ConditionField, type ConditionFieldType } from '@zerodep-dev/vue-tsx-pro-ui';
import { useDesignerMessages } from '../../lang/designer-messages';
import { outcomeKeys } from './editing';
import { compact, FieldGroup, optionsOf } from './fields';

/** EdgePanel 的属性 */
export interface EdgePanelProps {
  edge: ProcessEdge;
  definition: ProcessDefinition;
  manifest: FormManifest | null;
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

/** 条件里能用的字段：表单的字段（form.名字）与流程变量 */
export function conditionFields(definition: ProcessDefinition, manifest: FormManifest | null, formGroup: string, variableLabel: (name: string) => string): ConditionField[] {
  const fromForm = Object.entries(manifest ?? {}).flatMap(([name, field]): ConditionField[] => {
    const type = CONDITION_TYPES[field.type];
    if (!type) return [];
    const options = Array.isArray(field.options) ? field.options.map((o) => ({ value: o.value, label: o.label })) : undefined;
    return [{ name: `form.${name}`, label: field.label ?? name, type, group: formGroup, ...(options ? { options } : {}) }];
  });
  const variables = (definition.variables ?? []).flatMap((v): ConditionField[] => {
    const type = CONDITION_TYPES[v.type];
    return type ? [{ name: v.name, label: v.label ?? variableLabel(v.name), type }] : [];
  });
  return [...fromForm, ...variables];
}

/** 连线属性 */
export const EdgePanel = _component(({ edge, definition, manifest, onChange }: EdgePanelProps) => {
  const messages = useDesignerMessages();
  const set = (key: keyof ProcessEdge, value: unknown, merge?: string) => onChange(compact({ ...edge, [key]: value }) as ProcessEdge, merge);
  const source = () => definition.nodes.find((n) => n.id === edge.from);
  const nameOf = (id: string) => definition.nodes.find((n) => n.id === id)?.name || id;

  return () => {
    const from = source();
    const outcomes = from ? outcomeKeys(from) : [];
    const labelOf = (key: string) => (from?.type === 'task' ? (from.outcomes.find((o) => o.key === key)?.label ?? key) : key);
    const fromGateway = from !== undefined && GATEWAY_TYPES.has(from.type);
    return (
      <Flex direction="column" gap="md">
        <Small>{messages.edgeEnds(nameOf(edge.from), nameOf(edge.to))}</Small>
        <Field label={messages.id}>
          <TextField _value={edge.id} readonly />
        </Field>
        {from && BRANCHING_TYPES.has(from.type) && outcomes.length > 0 && (
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
              fields={conditionFields(definition, manifest, messages.form, messages.variable)}
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
