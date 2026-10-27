/**
 * 设计器右边什么都没选时的流程属性（0005 第 2 节）：标识、名称、分类、说明、表单、实例标题、待办的摘要列、撤回与处理人为空的规则。
 * 默认值同 0005 第 2 节（撤回：有人处理之前；处理人为空：转给管理员），选默认值时不写进定义。发起人范围（starters）、流程变量、通知、泳道在 JSON 里写。
 */
import type { FormManifest, ProcessDefinition, ProcessSettings } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Field, Flex, Select, Textarea, TextField } from '@zerodep-dev/vue-tsx-pro-ui';
import { useDesignerMessages } from '../../lang/designer-messages';
import { compact, optionsOf } from './fields';

/** ProcessPanel 的属性 */
export interface ProcessPanelProps {
  definition: ProcessDefinition;
  /** 能选的表单 */
  forms: readonly { key: string; name: string }[];
  /** 选的表单的字段清单（摘要列从里面选） */
  manifest: FormManifest | null;
  /** 已发布过的流程不能改标识（改了就是另一个流程） */
  keyLocked: boolean;
  onChange: (next: ProcessDefinition, merge?: string) => void;
}

/** 流程属性 */
export const ProcessPanel = _component(({ definition, forms, manifest, keyLocked, onChange }: ProcessPanelProps) => {
  const messages = useDesignerMessages();
  const set = (key: keyof ProcessDefinition, value: unknown, merge?: string) =>
    onChange(compact({ ...definition, [key]: value }) as ProcessDefinition, merge);
  const setting = (key: keyof ProcessSettings, value: unknown) => {
    const settings = compact({ ...definition.settings, [key]: value });
    set('settings', Object.keys(settings).length ? settings : undefined);
  };

  return () => (
    <Flex direction="column" gap="md">
      <Field label={messages.processKey}>
        <TextField _value={definition.key} readonly={keyLocked} onUpdate:_value={(key) => set('key', key, 'process:key')} />
      </Field>
      <Field label={messages.name}>
        <TextField _value={definition.name} onUpdate:_value={(name) => set('name', name, 'process:name')} />
      </Field>
      <Field label={messages.category}>
        <TextField _value={definition.category ?? ''} onUpdate:_value={(category) => set('category', category || undefined, 'process:category')} />
      </Field>
      <Field label={messages.form}>
        <Select
          items={[{ value: 'none', label: messages.noForm }, ...forms.map((f) => ({ value: f.key, label: f.name, description: f.key }))]}
          _value={definition.form?.key ?? 'none'}
          searchable
          onUpdate:_value={(key: string | null) => set('form', key && key !== 'none' ? { key } : undefined)}
        />
      </Field>
      <Field label={messages.titleTemplate} description={messages.titleHint}>
        <TextField _value={definition.title ?? ''} onUpdate:_value={(title) => set('title', title || undefined, 'process:title')} />
      </Field>
      {manifest && (
        <Field label={messages.summary}>
          <Select
            items={Object.entries(manifest).map(([name, field]) => ({ value: `form.${name}`, label: field.label ?? name }))}
            selectionMode="multiple"
            _value={[...(definition.summary ?? [])]}
            onUpdate:_value={(summary: string[]) => set('summary', summary.length ? summary : undefined)}
          />
        </Field>
      )}
      <Field label={messages.withdraw}>
        <Select
          items={optionsOf(messages.withdrawOptions)}
          _value={definition.settings?.withdraw ?? 'beforeAction'}
          onUpdate:_value={(mode: string | null) => setting('withdraw', mode === 'beforeAction' ? undefined : (mode ?? undefined))}
        />
      </Field>
      <Field label={messages.emptyAssignee}>
        <Select
          items={optionsOf(messages.emptyAssigneeOptions)}
          _value={definition.settings?.emptyAssignee ?? 'admin'}
          onUpdate:_value={(mode: string | null) => setting('emptyAssignee', mode === 'admin' ? undefined : (mode ?? undefined))}
        />
      </Field>
      <Field label={messages.description}>
        <Textarea _value={definition.description ?? ''} rows={2} onUpdate:_value={(text) => set('description', text || undefined, 'process:description')} />
      </Field>
    </Flex>
  );
});
