/**
 * 属性面板里反复用到的几个编辑器：时长、处理人、处理结果、表单字段的权限。都是受控的：拿到当前值，改了调 onChange 给出新值，
 * 不改传进来的对象（设计器按引用记撤销的每一步）。
 */
import { Plus, Trash2 } from '@lucide/icons';
import {
  isOrgId,
  type AssigneeSource,
  type Due,
  type FieldAccess,
  type FormManifest,
  type NodeFormAccess,
  type OrgId,
  type Outcome,
} from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import {
  Button,
  Checkbox,
  Field,
  Flex,
  IconButton,
  Label,
  NumberField,
  PeoplePicker,
  Select,
  Small,
  TagsInput,
  TextField,
  type PeopleEntry,
  type PeopleSource,
  type PeopleType,
} from '@zerodep-dev/vue-tsx-pro-ui';
import { useId, type VNodeChild } from 'vue';
import type { ExtensionEntry } from '../../client/types';
import { useDesignerMessages } from '../../lang/process-designer-messages';
import { entryOf, ExtensionSelect, JsonObjectField, ParamsForm } from './extensions';

/** 文案表（值 → 文字）排成下拉的选项 */
export const optionsOf = (labels: Readonly<Record<string, string>>) => Object.entries(labels).map(([value, label]) => ({ value, label }));

/** FieldGroup 的属性 */
export interface FieldGroupProps {
  label: string;
  children?: VNodeChild;
}

/**
 * 几个控件一组（处理人、处理结果、允许的操作……）：Field 会把它的标签给里面的每一个控件，勾选框自己的字就念不出来了；
 * 几个控件时改用 role="group" 加标题，每个控件留着自己的名字
 */
export const FieldGroup = _component(({ label, children }: FieldGroupProps) => {
  const id = useId();
  return () => (
    <Flex direction="column" gap="xs" role="group" aria-labelledby={id}>
      <Label id={id}>{label}</Label>
      {children}
    </Flex>
  );
});

/** 去掉值为 undefined 的键：写回定义时不留空的属性，JSON 保持干净 */
export function compact<T extends object>(value: T): T {
  return Object.fromEntries(Object.entries(value).filter(([, v]) => v !== undefined)) as T;
}

// ───────── 时长 ─────────

const UNITS = ['workdays', 'days', 'hours', 'minutes'] as const;
type Unit = (typeof UNITS)[number];

/** DurationField 的属性 */
export interface DurationFieldProps {
  value: Due | undefined;
  onChange: (next: Due | undefined) => void;
  label: string;
}

/** 时长：数量加单位；数量清空就是不限。写成"到某个时刻"（until）的不在这里改，原样显示在 JSON 里 */
export const DurationField = _component(({ value, onChange, label }: DurationFieldProps) => {
  const messages = useDesignerMessages();
  const unitOf = (): Unit => UNITS.find((u) => value !== undefined && u in value) ?? 'hours';
  const amountOf = (): number | null => {
    const unit = unitOf();
    return value !== undefined && unit in value ? (value as Record<Unit, number>)[unit] : null;
  };
  const write = (amount: number | null, unit: Unit) => onChange(amount == null ? undefined : ({ [unit]: amount } as Due));
  return () =>
    value !== undefined && 'until' in value ? (
      <Field label={label}>
        <Small>{JSON.stringify(value)}</Small>
      </Field>
    ) : (
      <FieldGroup label={label}>
        <Flex gap="xs">
          <NumberField
            _value={amountOf()}
            min={0}
            placeholder={messages.noDue}
            aria-label={`${label} ${messages.amount}`}
            onUpdate:_value={(next) => write(next == null ? null : Number(next), unitOf())}
          />
          <Select
            items={UNITS.map((u) => ({ value: u, label: messages.units[u] ?? u }))}
            _value={unitOf()}
            aria-label={`${label} ${messages.units['hours']}`}
            onUpdate:_value={(next: string | null) => next && write(amountOf() ?? 1, next as Unit)}
          />
        </Flex>
      </FieldGroup>
    );
});

// ───────── 处理人 ─────────

/** 选人选得出的三种来源：编号是字面量（不是取表单字段）时才在选择器里改 */
const ORG_KEYS = ['user', 'role', 'department'] as const satisfies readonly PeopleType[];
type OrgKey = (typeof ORG_KEYS)[number];

const orgKeyOf = (source: AssigneeSource): OrgKey | undefined =>
  ORG_KEYS.find((key) => key in source && isOrgId((source as Record<string, unknown>)[key]));

/** 解析器来源 */
type ResolverSource = Extract<AssigneeSource, { readonly resolver: string }>;

/** 上一步选人的来源 */
type PickSource = Extract<AssigneeSource, { readonly pick: unknown }>;

/** 能在面板里改的来源：人、角色、部门（字面量编号）、发起人、同某节点、解析器、上一步选 */
const editable = (source: AssigneeSource) =>
  orgKeyOf(source) !== undefined || 'initiator' in source || 'sameAs' in source || 'resolver' in source || 'pick' in source;

/** AssigneeField 的属性 */
export interface AssigneeFieldProps {
  value: readonly AssigneeSource[];
  onChange: (next: AssigneeSource[]) => void;
  label: string;
  /** 选人用的数据来源；没有时按编号填 */
  people?: PeopleSource | undefined;
  /** "与某节点的处理人相同"能选的节点 */
  nodes: readonly { id: string; name: string }[];
  /** 接入方注册的解析器（扩展目录）；没注册时不显示解析器这一块 */
  resolvers?: readonly ExtensionEntry[] | undefined;
  /** 能不能由上一步的人选（人工节点的处理人、会签的参与人能；抄送的收件人不能） */
  pickable?: boolean;
}

/**
 * 处理人（0005 的 3.2）：选人、角色、部门（记下名字快照 name），发起人，同某节点，解析器（从注册的里选，按声明填参数），
 * 上一步的人选（可限定从哪些人里选、最多几人）。取表单字段（{ ref }）、扩展种类在 JSON 里改，这里原样保留并提示有几项
 */
export const AssigneeField = _component(({ value, onChange, label, people, nodes, resolvers: registered = [], pickable = true }: AssigneeFieldProps) => {
  const messages = useDesignerMessages();
  const others = () => value.filter((s) => !editable(s));
  const orgSources = () => value.filter((s) => orgKeyOf(s) !== undefined);
  const sameAs = () => value.flatMap((s) => ('sameAs' in s ? [s.sameAs] : []));
  const resolvers = () => value.filter((s): s is ResolverSource => 'resolver' in s);
  const pick = () => value.find((s): s is PickSource => 'pick' in s)?.pick;

  /** 按面板里的几部分重新拼出来源：顺序是人员、发起人、同某节点、解析器，最后是面板里不改的 */
  function write(parts: { org?: AssigneeSource[]; initiator?: boolean; sameAs?: string[]; resolvers?: ResolverSource[]; pick?: PickSource['pick'] | null }) {
    const initiator = parts.initiator ?? value.some((s) => 'initiator' in s);
    const picking = parts.pick === undefined ? pick() : parts.pick;
    onChange([
      ...(parts.org ?? orgSources()),
      ...(initiator ? [{ initiator: true as const }] : []),
      ...(parts.sameAs ?? sameAs()).map((id) => ({ sameAs: id })),
      ...(parts.resolvers ?? resolvers()),
      ...(picking ? [{ pick: picking }] : []),
      ...others(),
    ]);
  }

  /** 上一步选人时能选的范围：人、角色、部门；范围里别的来源原样保留 */
  const pickEntries = (): PeopleEntry[] =>
    (pick()?.from ?? []).flatMap((s) => {
      const type = orgKeyOf(s);
      if (!type) return [];
      const id = (s as Record<string, unknown>)[type] as OrgId;
      return [{ type, id: String(id), name: ('name' in s && s.name) || messages.orgEntry(type, String(id)) }];
    });
  const writePick = (change: { from?: AssigneeSource[]; max?: number | undefined }) => {
    const current = pick() ?? {};
    const from = change.from ?? current.from ?? [];
    write({ pick: compact({ ...current, from: from.length ? from : undefined, ...('max' in change ? { max: change.max } : {}) }) });
  };

  const entries = (): PeopleEntry[] =>
    orgSources().map((s) => {
      const type = orgKeyOf(s)!;
      const id = (s as Record<string, unknown>)[type] as OrgId;
      return { type, id: String(id), name: ('name' in s && s.name) || messages.orgEntry(type, String(id)) };
    });
  const fromEntries = (picked: readonly PeopleEntry[]): AssigneeSource[] =>
    picked.map((e) => ({ [e.type]: toOrgId(e.id), name: e.name }) as AssigneeSource);

  /** 没有选人的数据来源时：按种类填编号 */
  const idsOf = (key: OrgKey) => orgSources().flatMap((s) => (orgKeyOf(s) === key ? [String((s as Record<string, unknown>)[key])] : []));
  const withIds = (key: OrgKey, ids: readonly string[]) => [
    ...orgSources().filter((s) => orgKeyOf(s) !== key),
    ...ids.filter((id) => isOrgId(id)).map((id) => ({ [key]: toOrgId(id) }) as AssigneeSource),
  ];

  return () => (
    <FieldGroup label={label}>
      <Flex direction="column" gap="sm">
        {people ? (
          <PeoplePicker
            source={people}
            types={['user', 'role', 'department']}
            _value={entries()}
            placeholder={messages.pickPeople}
            aria-label={`${label} ${messages.pickPeople}`}
            onUpdate:_value={(picked) => write({ org: fromEntries(picked) })}
          />
        ) : (
          ORG_KEYS.map((key) => (
            <TagsInput
              key={key}
              _value={idsOf(key)}
              placeholder={messages[`${key}Ids`]}
              aria-label={`${label} ${messages[`${key}Ids`]}`}
              onUpdate:_value={(ids) => write({ org: withIds(key, ids) })}
            />
          ))
        )}
        <Checkbox _checked={value.some((s) => 'initiator' in s)} onUpdate:_checked={(checked) => write({ initiator: checked })}>
          {messages.initiator}
        </Checkbox>
        {nodes.length > 0 && (
          <Select
            items={nodes.map((n) => ({ value: n.id, label: n.name }))}
            selectionMode="multiple"
            _value={sameAs()}
            placeholder={messages.sameAs}
            aria-label={messages.sameAs}
            onUpdate:_value={(ids: string[]) => write({ sameAs: ids })}
          />
        )}
        {resolvers().map((source, i) => {
          const entry = entryOf(registered, source.resolver);
          const replace = (next: ResolverSource | null) =>
            write({ resolvers: resolvers().flatMap((s, j) => (j !== i ? [s] : next ? [next] : [])) });
          return (
            <Flex key={i} direction="column" gap="xs">
              <Flex gap="xs" align="center">
                <ExtensionSelect
                  entries={registered}
                  value={source.resolver || null}
                  label={`${label} ${messages.resolver} ${i + 1}`}
                  onChange={(name) => replace(name ? { resolver: name } : null)}
                />
                <IconButton icon={Trash2} variant="ghost" aria-label={messages.removeResolver(entry?.label ?? source.resolver)} onClick={() => replace(null)} />
              </Flex>
              {entry && entry.params.length > 0 ? (
                <ParamsForm
                  params={entry.params}
                  value={source.args}
                  mergeKey={`resolver:${i}`}
                  onChange={(args) => replace(compact({ resolver: source.resolver, args }))}
                />
              ) : (
                source.args && <JsonObjectField label={messages.args} value={source.args} onChange={(args) => replace(compact({ resolver: source.resolver, args }))} />
              )}
            </Flex>
          );
        })}
        {pickable && (
          <Checkbox _checked={pick() !== undefined} onUpdate:_checked={(checked) => write({ pick: checked ? {} : null })}>
            {messages.pickByPrevious}
          </Checkbox>
        )}
        {pick() !== undefined && (
          <Flex direction="column" gap="xs" style={{ paddingInlineStart: '1.5rem' }}>
            {people && (
              <PeoplePicker
                source={people}
                types={['user', 'role', 'department']}
                _value={pickEntries()}
                placeholder={messages.pickFrom}
                aria-label={`${label} ${messages.pickFrom}`}
                onUpdate:_value={(picked) =>
                  writePick({ from: [...fromEntries(picked), ...(pick()?.from ?? []).filter((s) => orgKeyOf(s) === undefined)] })
                }
              />
            )}
            <NumberField
              _value={pick()?.max ?? null}
              min={1}
              placeholder={messages.pickMaxAny}
              aria-label={`${label} ${messages.pickMax}`}
              onUpdate:_value={(max) => writePick({ max: max == null ? undefined : Number(max) })}
            />
          </Flex>
        )}
        {registered.length > 0 && (
          <Button variant="outline" size="sm" icon={Plus} onClick={() => write({ resolvers: [...resolvers(), { resolver: registered[0]!.name }] })}>
            {messages.addResolver}
          </Button>
        )}
        {others().length > 0 && <Small>{messages.otherSources(others().length)}</Small>}
      </Flex>
    </FieldGroup>
  );
});

/** 选择器给的编号是文字：在安全整数范围内写成数字（同手写的定义），超出时保留文字 */
function toOrgId(id: string | number): OrgId {
  const n = Number(id);
  return Number.isSafeInteger(n) ? n : (String(id) as OrgId);
}

// ───────── 处理结果 ─────────

/** OutcomeList 的属性 */
export interface OutcomeListProps {
  value: readonly Outcome[];
  onChange: (next: Outcome[], merge?: string) => void;
  /** 接入方注册的守卫（扩展目录）：每个结果能选提交前要过哪些检查 */
  guards?: readonly ExtensionEntry[] | undefined;
}

/**
 * 人工节点的处理结果（0005 的 5.3）：标识、按钮上的字、种类（退回时可以处理时选退回到哪一步，不用连线）、意见必填、提交前检查（守卫）；
 * 别的属性（确认……）原样保留
 */
export const OutcomeList = _component(({ value, onChange, guards = [] }: OutcomeListProps) => {
  const messages = useDesignerMessages();
  const patch = (i: number, change: Partial<Outcome>, merge?: string) =>
    onChange(
      value.map((o, j) => (j === i ? compact({ ...o, ...change }) : o)),
      merge,
    );
  const add = () => {
    const keys = new Set(value.map((o) => o.key));
    let n = value.length + 1;
    while (keys.has(`outcome${n}`)) n++;
    onChange([...value, { key: `outcome${n}`, label: messages.newOutcome(n) }]);
  };
  return () => (
    <FieldGroup label={messages.outcomes}>
      <Flex direction="column" gap="sm">
        {value.map((outcome, i) => (
          <Flex key={i} gap="xs" align="center" wrap>
            <TextField
              _value={outcome.key}
              aria-label={`${messages.outcomeKey} ${i + 1}`}
              style={{ width: '7rem' }}
              onUpdate:_value={(key) => patch(i, { key }, `outcome:${i}:key`)}
            />
            <TextField
              _value={outcome.label}
              aria-label={`${messages.outcomeLabel} ${i + 1}`}
              style={{ width: '7rem' }}
              onUpdate:_value={(label) => patch(i, { label }, `outcome:${i}:label`)}
            />
            <Select
              items={optionsOf(messages.outcomeKinds)}
              _value={outcome.kind ?? 'none'}
              aria-label={`${messages.outcomeKind} ${i + 1}`}
              // 不是退回了：处理时选退回节点（target）跟着去掉
              onUpdate:_value={(kind: string | null) =>
                patch(i, { kind: (kind === 'none' ? undefined : kind) as Outcome['kind'], target: kind === 'return' ? outcome.target : undefined })
              }
            />
            {outcome.kind === 'return' && (
              <Checkbox _checked={outcome.target === 'choose'} onUpdate:_checked={(checked) => patch(i, { target: checked ? 'choose' : undefined })}>
                {messages.returnChoose}
              </Checkbox>
            )}
            <Checkbox
              _checked={outcome.comment === 'required'}
              onUpdate:_checked={(checked) => patch(i, { comment: checked ? 'required' : undefined })}
            >
              {messages.commentRequired}
            </Checkbox>
            {(guards.length > 0 || (outcome.guards?.length ?? 0) > 0) && (
              <Select
                items={[
                  ...guards.map((g) => ({ value: g.name, label: g.label, description: g.description ?? g.name })),
                  ...(outcome.guards ?? []).filter((name) => !guards.some((g) => g.name === name)).map((name) => ({ value: name, label: messages.unregistered(name) })),
                ]}
                selectionMode="multiple"
                _value={[...(outcome.guards ?? [])]}
                placeholder={messages.guards}
                aria-label={`${messages.guards} ${i + 1}`}
                onUpdate:_value={(names: string[]) => patch(i, { guards: names.length ? names : undefined })}
              />
            )}
            <IconButton
              icon={Trash2}
              aria-label={messages.removeOutcome(outcome.label)}
              variant="ghost"
              onClick={() => onChange(value.filter((_, j) => j !== i))}
            />
          </Flex>
        ))}
        <Button variant="outline" size="sm" icon={Plus} onClick={add}>
          {messages.addOutcome}
        </Button>
      </Flex>
    </FieldGroup>
  );
});

// ───────── 表单字段的权限 ─────────

/** FormAccessTable 的属性 */
export interface FormAccessTableProps {
  value: NodeFormAccess | undefined;
  onChange: (next: NodeFormAccess | undefined) => void;
  /** 流程选的表单的字段清单；没选表单时为 null */
  manifest: FormManifest | null;
}

/** 节点上的字段权限（0004 的 4 档）：没列出的字段用 default，每个字段可以单独改 */
export const FormAccessTable = _component(({ value, onChange, manifest }: FormAccessTableProps) => {
  const messages = useDesignerMessages();
  const levels = () => optionsOf(Object.fromEntries(Object.entries(messages.access).filter(([k]) => k !== 'none')));
  const write = (next: NodeFormAccess) => {
    const fields = Object.fromEntries(Object.entries(next.fields ?? {}).filter(([, v]) => v !== undefined));
    const access = compact({ default: next.default, fields: Object.keys(fields).length ? fields : undefined });
    onChange(Object.keys(access).length ? access : undefined);
  };
  return () =>
    manifest ? (
      <FieldGroup label={messages.formAccess}>
        <Flex direction="column" gap="xs">
          <Flex gap="sm" align="center" justify="between">
            <Small>{messages.defaultAccess}</Small>
            <Select
              items={levels()}
              _value={value?.default ?? null}
              placeholder={messages.access['none']}
              clearable
              aria-label={messages.defaultAccess}
              onUpdate:_value={(level: string | null) => write({ ...value, default: (level ?? undefined) as FieldAccess | undefined })}
            />
          </Flex>
          {Object.entries(manifest).map(([name, field]) => (
            <Flex key={name} gap="sm" align="center" justify="between">
              <Small>{field.label ?? name}</Small>
              <Select
                items={levels()}
                _value={value?.fields?.[name] ?? null}
                placeholder={messages.access['none']}
                clearable
                aria-label={field.label ?? name}
                onUpdate:_value={(level: string | null) =>
                  write({ ...value, fields: { ...value?.fields, [name]: (level ?? undefined) as FieldAccess } })
                }
              />
            </Flex>
          ))}
        </Flex>
      </FieldGroup>
    ) : (
      <Field label={messages.formAccess}>
        <Small>{messages.noFormFields}</Small>
      </Field>
    );
});

