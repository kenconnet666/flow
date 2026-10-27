/**
 * 设计器右边的节点属性：通用的标识、名称、说明，加上这种节点的常用设置（0005 第 5 节）。
 * 面板里没有的设置（提醒、超时的处理、无人认领、跳过规则……）在 JSON 里写，这里原样保留。
 */
import type {
  AllowedActions,
  CallNode,
  CcNode,
  EndNode,
  FormManifest,
  MultiCompletion,
  ProcessDefinition,
  ProcessNode,
  ReceiveNode,
  ServiceNode,
  StartNode,
  TaskNode,
  TimerNode,
} from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import {
  Checkbox,
  Field,
  Flex,
  NumberField,
  Select,
  Switch,
  TagsInput,
  Textarea,
  TextField,
  type PeopleSource,
} from '@zerodep-dev/vue-tsx-pro-ui';
import { useDesignerMessages } from '../../lang/designer-messages';
import { AssigneeField, compact, DurationField, FieldGroup, FormAccessTable, optionsOf, OutcomeList } from './fields';

/** NodePanel 的属性 */
export interface NodePanelProps {
  node: ProcessNode;
  definition: ProcessDefinition;
  /** 流程选的表单的字段清单 */
  manifest: FormManifest | null;
  people?: PeopleSource | undefined;
  /** 调用流程能选的流程 */
  processes: readonly { key: string; name: string }[];
  /** 改了节点；merge 相同的连续修改（逐字输入）合成一步撤销 */
  onChange: (next: ProcessNode, merge?: string) => void;
}

/** 节点、连线的标识：字母开头，字母、数字、- 与 _ */
export const ID_PATTERN = /^[A-Za-z][A-Za-z0-9_-]*$/;

/** 节点属性 */
export const NodePanel = _component(({ node, definition, manifest, people, processes, onChange }: NodePanelProps) => {
  const messages = useDesignerMessages();
  let idDraft = _state<string | null>(null);

  /** 改一个属性；值是 undefined 时去掉这个键 */
  const set = (key: string, value: unknown, merge?: string) => onChange(compact({ ...node, [key]: value }) as ProcessNode, merge);
  const taskNodes = () => definition.nodes.filter((n) => n.type === 'task' && n.id !== node.id).map((n) => ({ id: n.id, name: n.name || n.id }));

  /** 标识输入完（失去焦点）才改：改标识要改连线、布局，逐字改会把半截的标识写进去 */
  function commitId() {
    const next = idDraft?.trim();
    idDraft = null;
    if (!next || next === node.id) return;
    if (!ID_PATTERN.test(next) || definition.nodes.some((n) => n.id === next)) return;
    onChange({ ...node, id: next });
  }
  const idInvalid = () => idDraft !== null && idDraft.trim() !== node.id && (!ID_PATTERN.test(idDraft.trim()) || definition.nodes.some((n) => n.id === idDraft!.trim()));

  const common = () => (
    <>
      <Field label={messages.id} invalid={idInvalid()} error={idInvalid() ? messages.idInvalid : undefined}>
        <TextField
          _value={idDraft ?? node.id}
          onUpdate:_value={(text) => (idDraft = text)}
          onBlur={commitId}
          onKeydown={(event: KeyboardEvent) => event.key === 'Enter' && commitId()}
        />
      </Field>
      <Field label={messages.name}>
        <TextField _value={node.name} onUpdate:_value={(name) => set('name', name, `${node.id}:name`)} />
      </Field>
    </>
  );

  const description = () => (
    <Field label={messages.description}>
      <Textarea _value={node.description ?? ''} rows={2} onUpdate:_value={(text) => set('description', text || undefined, `${node.id}:description`)} />
    </Field>
  );

  const start = (n: StartNode) => <FormAccessTable value={n.form} manifest={manifest} onChange={(form) => set('form', form)} />;

  const task = (n: TaskNode) => {
    const multi = n.multi;
    const setMulti = (patch: Partial<NonNullable<TaskNode['multi']>> | null) =>
      set('multi', patch === null ? undefined : compact({ participants: multi?.participants ?? [], complete: 'all' as MultiCompletion, ...multi, ...patch }));
    return (
      <>
        <AssigneeField
          label={messages.assignees}
          value={n.assignees ?? []}
          people={people}
          nodes={taskNodes()}
          onChange={(sources) => set('assignees', sources)}
        />
        <Field label={messages.multi}>
          <Select
            items={optionsOf(messages.multiOptions)}
            _value={multi?.complete ?? 'none'}
            onUpdate:_value={(mode: string | null) => (mode && mode !== 'none' ? setMulti({ complete: mode as MultiCompletion }) : setMulti(null))}
          />
        </Field>
        {multi && Array.isArray(multi.participants) && (
          <AssigneeField
            label={messages.participants}
            value={multi.participants}
            people={people}
            nodes={taskNodes()}
            onChange={(participants) => setMulti({ participants })}
          />
        )}
        {multi?.complete === 'ratio' && (
          <Field label={messages.ratio}>
            <NumberField _value={multi.ratio ?? null} min={0} max={1} step={0.1} onUpdate:_value={(ratio) => setMulti({ ratio: ratio == null ? undefined : Number(ratio) })} />
          </Field>
        )}
        {multi?.complete === 'votes' && (
          <Field label={messages.votes}>
            <NumberField _value={multi.votes ?? null} min={1} onUpdate:_value={(votes) => setMulti({ votes: votes == null ? undefined : Number(votes) })} />
          </Field>
        )}
        {(multi?.complete === 'ratio' || multi?.complete === 'votes') && (
          <Field label={messages.fallback}>
            <Select
              items={n.outcomes.map((o) => ({ value: o.key, label: o.label }))}
              _value={multi.fallback ?? null}
              clearable
              onUpdate:_value={(fallback: string | null) => setMulti({ fallback: fallback ?? undefined })}
            />
          </Field>
        )}
        <OutcomeList value={n.outcomes} onChange={(outcomes, merge) => set('outcomes', outcomes, merge && `${n.id}:${merge}`)} />
        <DurationField label={messages.due} value={n.due} onChange={(due) => set('due', due, `${n.id}:due`)} />
        <Field label={messages.emptyAssignee}>
          <Select
            items={[{ value: 'inherit', label: messages.inherit }, ...optionsOf(messages.emptyAssigneeOptions)]}
            _value={n.emptyAssignee ?? 'inherit'}
            onUpdate:_value={(mode: string | null) => set('emptyAssignee', mode === 'inherit' ? undefined : (mode ?? undefined))}
          />
        </Field>
        <FieldGroup label={messages.allow}>
          <Flex gap="sm" wrap>
            {Object.entries(messages.allowOptions).map(([key, label]) => (
              <Checkbox
                key={key}
                _checked={n.allow?.[key as keyof AllowedActions] !== false}
                onUpdate:_checked={(checked) => {
                  const allow = compact({ ...n.allow, [key]: checked ? undefined : false });
                  set('allow', Object.keys(allow).length ? allow : undefined);
                }}
              >
                {label}
              </Checkbox>
            ))}
          </Flex>
        </FieldGroup>
        <FormAccessTable value={n.form} manifest={manifest} onChange={(form) => set('form', form)} />
      </>
    );
  };

  const service = (n: ServiceNode) => (
    <>
      <Field label={messages.action}>
        <TextField _value={n.action} onUpdate:_value={(action) => set('action', action, `${n.id}:action`)} />
      </Field>
      <JsonObjectField label={messages.args} value={n.args} onChange={(args) => set('args', args)} />
      <Field label={messages.retry}>
        <NumberField
          _value={n.retry?.times ?? null}
          min={0}
          onUpdate:_value={(times) => set('retry', times == null ? undefined : compact({ ...n.retry, times: Number(times) }))}
        />
      </Field>
      <Field label={messages.onFailure}>
        <Select
          items={optionsOf(messages.onFailureOptions)}
          _value={n.onFailure ?? 'manual'}
          onUpdate:_value={(mode: string | null) => set('onFailure', mode === 'error' ? 'error' : undefined)}
        />
      </Field>
    </>
  );

  const timer = (n: TimerNode) => <DurationField label={messages.wait} value={n.wait} onChange={(wait) => set('wait', wait ?? { hours: 1 }, `${n.id}:wait`)} />;

  const receive = (n: ReceiveNode) => (
    <>
      <Field label={messages.event}>
        <TextField _value={n.event} onUpdate:_value={(event) => set('event', event, `${n.id}:event`)} />
      </Field>
      <Field label={messages.receiveOutcomes}>
        <TagsInput _value={[...(n.outcomes ?? [])]} onUpdate:_value={(outcomes) => set('outcomes', outcomes.length ? outcomes : undefined)} />
      </Field>
      <DurationField label={messages.due} value={n.due} onChange={(due) => set('due', due, `${n.id}:due`)} />
    </>
  );

  const cc = (n: CcNode) => (
    <>
      <AssigneeField label={messages.ccTo} value={n.to} people={people} nodes={taskNodes()} onChange={(to) => set('to', to)} />
      <Field label={messages.message}>
        <Textarea _value={n.message ?? ''} rows={2} onUpdate:_value={(text) => set('message', text || undefined, `${n.id}:message`)} />
      </Field>
    </>
  );

  const call = (n: CallNode) => (
    <>
      <Field label={messages.callProcess}>
        <Select
          items={processes.filter((p) => p.key !== definition.key).map((p) => ({ value: p.key, label: p.name, description: p.key }))}
          _value={n.process || null}
          searchable
          onUpdate:_value={(key: string | null) => set('process', key ?? '')}
        />
      </Field>
      <Field label={messages.callMode}>
        <Select items={optionsOf(messages.callModes)} _value={n.mode ?? 'sync'} onUpdate:_value={(mode: string | null) => set('mode', mode === 'async' ? 'async' : undefined)} />
      </Field>
    </>
  );

  const end = (n: EndNode) => (
    <>
      <Field label={messages.result}>
        <TextField _value={n.result ?? ''} onUpdate:_value={(result) => set('result', result || undefined, `${n.id}:result`)} />
      </Field>
      <Switch _checked={n.terminate === true} onUpdate:_checked={(on) => set('terminate', on || undefined)}>
        {messages.terminate}
      </Switch>
    </>
  );

  const specific = () => {
    switch (node.type) {
      case 'start':
        return start(node);
      case 'task':
        return task(node);
      case 'service':
        return service(node);
      case 'timer':
        return timer(node);
      case 'receive':
        return receive(node);
      case 'cc':
        return cc(node);
      case 'call':
        return call(node);
      case 'end':
        return end(node);
      default:
        return null;
    }
  };

  return () => (
    <Flex direction="column" gap="md">
      {common()}
      {specific()}
      {description()}
    </Flex>
  );
});

/** JsonObjectField 的属性 */
interface JsonObjectFieldProps {
  label: string;
  value: Readonly<Record<string, unknown>> | undefined;
  onChange: (next: Record<string, unknown> | undefined) => void;
}

/** 一个 JSON 对象（自动节点的参数）：写成合法的对象才写回，写错时提示、不改 */
const JsonObjectField = _component(({ label, value, onChange }: JsonObjectFieldProps) => {
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
