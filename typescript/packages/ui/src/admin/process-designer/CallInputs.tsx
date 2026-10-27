/**
 * 调用流程时传给子流程的值（0005 的 4.2 `input`）：左边是子流程那一侧（它的表单字段 form.名字、流程变量），右边取父流程这一侧的哪个值
 * （写成 `{ ref }`）。两边表单有同名字段时一键全带上。右边是固定值（不是引用）的项在这里原样显示，在 JSON 里改。
 */
import { Plus, Trash2 } from '@lucide/icons';
import { isRef, parseDefinition, type CallNode, type FormManifest, type ProcessDefinition } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Button, Flex, IconButton, Select, Small, useRequest, type ConditionField } from '@zerodep-dev/vue-tsx-pro-ui';
import { useFlowClient } from '../../client/flow-client';
import { useDesignerMessages } from '../../lang/process-designer-messages';
import { conditionFields } from './EdgePanel';
import { FieldGroup } from './fields';

/** CallInputs 的属性 */
export interface CallInputsProps {
  node: CallNode;
  /** 父流程的定义与表单的字段清单 */
  definition: ProcessDefinition;
  manifest: FormManifest | null;
  onChange: (input: Record<string, unknown> | undefined) => void;
}

export const CallInputs = _component(({ node, definition, manifest, onChange }: CallInputsProps) => {
  const flow = useFlowClient();
  const messages = useDesignerMessages();
  // 子流程最新版本的定义与它的表单：列出子流程那一侧能写的名字
  const child = useRequest(
    async (key: string | null, signal) => {
      if (!key) return null;
      const childDefinition = parseDefinition((await flow.definitionJson(key, undefined, signal)).json);
      const form = childDefinition.form?.key;
      const childManifest = form ? (JSON.parse((await flow.formDocument(form, undefined, signal)).manifest) as FormManifest) : null;
      return { definition: childDefinition, manifest: childManifest };
    },
    { params: () => node.process || null },
  );

  const fieldsOf = (d: ProcessDefinition, m: FormManifest | null): ConditionField[] => conditionFields(d, m, messages.form, messages.variable);
  const targets = () => (child.data ? fieldsOf(child.data.definition, child.data.manifest) : []);
  const sources = () => fieldsOf(definition, manifest);
  const entries = () => Object.entries(node.input ?? {});
  const write = (next: [string, unknown][]) => onChange(next.length ? Object.fromEntries(next) : undefined);
  /** 加一项：右边先取同名的，没有时取第一个 */
  const add = (target: string) => {
    const source = sources().find((s) => s.name === target) ?? sources()[0];
    if (source) write([...entries(), [target, { ref: source.name }]]);
  };
  /** 子流程那一侧还没传的、父流程这一侧有同名的 */
  const sameNamed = () => targets().filter((t) => !(t.name in (node.input ?? {})) && sources().some((s) => s.name === t.name));

  return () => (
    <FieldGroup label={messages.callInput}>
      <Flex direction="column" gap="xs">
        {entries().map(([target, value], i) => (
          <Flex key={target} gap="xs" align="center">
            <Select
              items={targets().map((t) => ({ value: t.name, label: t.label, description: t.name }))}
              _value={target}
              aria-label={`${messages.callInputTarget} ${i + 1}`}
              onUpdate:_value={(name: string | null) => name && write(entries().map(([k, v]) => (k === target ? [name, v] : [k, v])))}
            />
            <Small>←</Small>
            {isRef(value) ? (
              <Select
                items={sources().map((s) => ({ value: s.name, label: s.label, description: s.name }))}
                _value={value.ref}
                aria-label={`${messages.callInputSource} ${i + 1}`}
                onUpdate:_value={(ref: string | null) => ref && write(entries().map(([k, v]) => (k === target ? [k, { ref }] : [k, v])))}
              />
            ) : (
              <Small>{JSON.stringify(value)}</Small>
            )}
            <IconButton icon={Trash2} variant="ghost" aria-label={messages.removeCallInput(target)} onClick={() => write(entries().filter(([k]) => k !== target))} />
          </Flex>
        ))}
        <Flex gap="xs" wrap>
          {targets().some((t) => !(t.name in (node.input ?? {}))) && (
            <Button
              variant="outline"
              size="sm"
              icon={Plus}
              onClick={() => add(targets().find((t) => !(t.name in (node.input ?? {})))!.name)}
            >
              {messages.addCallInput}
            </Button>
          )}
          {sameNamed().length > 0 && (
            <Button variant="outline" size="sm" onClick={() => write([...entries(), ...sameNamed().map((t): [string, unknown] => [t.name, { ref: t.name }])])}>
              {messages.callInputSameNames(sameNamed().length)}
            </Button>
          )}
        </Flex>
      </Flex>
    </FieldGroup>
  );
});
