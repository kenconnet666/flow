/**
 * 流程定义的编辑（管理端）：两种写法，改的是同一份 0005 的定义——
 *   - 设计：流程设计器 FlowDesigner（画布、属性面板，边画边按模型包的规则校验）；
 *   - JSON：在代码编辑器里直接改，设计器没做成面板的设置（提醒、通知、泳道……）在这里写。
 * 换写法时互相转：设计换到 JSON 时按当前的定义排版；JSON 换回设计时要是合法的 JSON。"校验"交给后端列出错误与警告（不发布），
 * "发布"定一个新版本。打开已有的流程时载入最新版本，也能载入以前的某个版本再改；新建时给一个最小的骨架。
 *
 *   <FlowDefinitionEditor processKey={key} people={peopleSource} onPublished={(result) => toaster.success(…)} />
 */
import type { ProcessDefinition } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import {
  Alert,
  Button,
  Card,
  Field,
  Flex,
  HttpError,
  Select,
  Tab,
  TabList,
  Tabs,
  Text,
  TextField,
  useErrorMessage,
  useRequest,
  type PeopleSource,
} from '@zerodep-dev/vue-tsx-pro-ui';
import { CodeEditor } from '@zerodep-dev/vue-tsx-pro-ui/code-editor';
import { watch } from 'vue';
import { useFlowClient } from '../client/flow-client';
import type { DefinitionProblem, PublishedDefinition } from '../client/types';
import { useDesignerMessages } from '../lang/designer-messages';
import { useFlowMessages } from '../lang/messages';
import { FlowDesigner } from './designer/FlowDesigner';

/** FlowDefinitionEditor 的属性 */
export interface FlowDefinitionEditorProps {
  /** 要编辑的流程；新建时不给 */
  processKey?: string;
  /** 设计器里选处理人用的组织数据 */
  people?: PeopleSource;
  /** 发布成功 */
  onPublished?: (result: PublishedDefinition) => void;
}

/** 写法 */
type Mode = 'design' | 'json';

/** 新建时的骨架：一个人工节点，通过就结束 */
const SKELETON: ProcessDefinition = {
  key: 'new-process',
  name: '新流程',
  nodes: [
    { id: 'start', type: 'start', name: '提交' },
    { id: 'approve', type: 'task', name: '审批', assignees: [{ initiator: true }], outcomes: [{ key: 'ok', label: '同意' }] },
    { id: 'end', type: 'end', name: '结束', result: 'approved' },
  ],
  edges: [
    { id: 'submit', from: 'start', to: 'approve' },
    { id: 'approved', from: 'approve', to: 'end', outcome: 'ok' },
  ],
};

/** 排版好的 JSON */
const pretty = (definition: ProcessDefinition) => JSON.stringify(definition, null, 2);

/** 流程定义的编辑 */
export const FlowDefinitionEditor = _component(({ processKey, people, onPublished }: FlowDefinitionEditorProps) => {
  const flow = useFlowClient();
  const messages = useFlowMessages();
  const designerMessages = useDesignerMessages();
  const errorText = useErrorMessage();
  let mode = _state<Mode>('design');
  // 定义只整体换（设计器每改一步给出新的一份），不做深层代理
  let definition = _state.raw<ProcessDefinition>(SKELETON);
  let text = _state(pretty(SKELETON));
  let loaded = _state(!processKey);
  let note = _state('');
  let problems = _state<readonly DefinitionProblem[] | null>(null);
  let failure = _state<string | null>(null);
  let notice = _state<string | null>(null);
  let busy = _state<string | null>(null);

  /** 现在写的这一份的 JSON 文字：设计时按定义排版，JSON 时就是编辑器里的字 */
  const currentJson = () => (mode === 'design' ? pretty(definition) : text);

  // 改了就不再是上次校验的那一份：校验结果收起来
  watch(
    () => [definition, text],
    () => (problems = null),
  );

  const versions = useRequest((key: string | null, signal) => (key ? flow.versions(key, signal) : Promise.resolve([])), {
    params: () => processKey ?? null,
  });

  /** 载入一个版本（不给时最新的）；不是合法的定义 JSON 时只能在 JSON 里改 */
  async function load(version?: number) {
    if (!processKey) return;
    failure = null;
    problems = null;
    try {
      const json = (await flow.definitionJson(processKey, version)).json;
      try {
        definition = JSON.parse(json) as ProcessDefinition;
        text = pretty(definition);
      } catch {
        text = json;
        mode = 'json';
      }
      loaded = true;
    } catch (error) {
      failure = errorText(error);
    }
  }
  void load();

  /** 换写法：设计 → JSON 按定义排版；JSON → 设计要读得出 JSON，读不出时留在 JSON 并提示 */
  function switchMode(next: Mode) {
    if (next === mode) return;
    if (next === 'json') {
      text = pretty(definition);
    } else {
      try {
        definition = JSON.parse(text) as ProcessDefinition;
      } catch (error) {
        failure = messages.jsonInvalid((error as Error).message);
        return;
      }
    }
    failure = null;
    mode = next;
  }

  async function validate() {
    busy = 'validate';
    failure = null;
    notice = null;
    try {
      problems = (await flow.validateDefinition(currentJson())).problems;
    } catch (error) {
      failure = errorText(error);
    } finally {
      busy = null;
    }
  }

  async function publish() {
    busy = 'publish';
    failure = null;
    notice = null;
    try {
      const result = await flow.publishDefinition(currentJson(), note.trim() || undefined);
      problems = null;
      note = '';
      notice = messages.published(result.version);
      await versions.refresh();
      onPublished?.(result);
    } catch (error) {
      // 校验没过：明细就是问题列表，照校验的样子列出来
      if (error instanceof HttpError && error.code === 'flow.definition-invalid' && error.errors?.length) {
        problems = error.errors.map((d) => ({
          code: d.code ?? 'unknown',
          severity: 'error' as const,
          node: (d['node'] as string | undefined) ?? null,
          edge: (d['edge'] as string | undefined) ?? null,
          lane: (d['lane'] as string | undefined) ?? null,
          field: d.field ?? null,
          params: (d.params ?? {}) as Record<string, string>,
        }));
      } else {
        failure = errorText(error);
      }
    } finally {
      busy = null;
    }
  }

  /** 问题出在哪：节点、连线、泳道、字段 */
  const where = (p: DefinitionProblem) => [p.node, p.edge, p.lane, p.field].filter(Boolean).join(' / ');

  return () => (
    <Card header={processKey ? `${messages.definitionEditor}：${processKey}` : messages.newProcess}>
      <Flex direction="column" gap="md">
        {failure && <Alert tone="danger" onClose={() => (failure = null)}>{failure}</Alert>}
        {notice && <Alert tone="success" onClose={() => (notice = null)}>{notice}</Alert>}
        <Flex gap="md" align="end" justify="between" wrap>
          <Tabs _value={mode} onUpdate:_value={(next: string) => switchMode(next as Mode)}>
            <TabList aria-label={messages.definitionEditor}>
              <Tab value="design">{designerMessages.design}</Tab>
              <Tab value="json">{designerMessages.json}</Tab>
            </TabList>
          </Tabs>
          {processKey && (versions.data?.length ?? 0) > 0 && (
            <Field label={messages.loadVersion}>
              <Select
                items={(versions.data ?? []).map((v) => ({
                  value: String(v.version),
                  label: `v${v.version}${v.note ? ` · ${v.note}` : ''}`,
                  description: v.publishedByName ?? undefined,
                }))}
                placeholder={messages.loadVersion}
                onUpdate:_value={(value: string | null) => value && void load(Number(value))}
              />
            </Field>
          )}
        </Flex>
        {loaded &&
          (mode === 'design' ? (
            <FlowDesigner _value={definition} people={people} keyLocked={Boolean(processKey)} />
          ) : (
            <CodeEditor _value={text} aria-label={messages.definitionEditor} style={{ height: '28rem' }} />
          ))}
        {problems &&
          (problems.length === 0 ? (
            <Alert tone="success">{messages.noProblems}</Alert>
          ) : (
            <Alert tone={problems.some((p) => p.severity === 'error') ? 'danger' : 'warning'}>
              <Flex direction="column" gap="xs">
                {problems.map((p, i) => (
                  <Text key={i}>
                    {p.severity === 'error' ? '✕' : '!'} {messages.problem(p.code, p.params)}
                    {where(p) ? `（${messages.problemAt(where(p))}）` : ''}
                  </Text>
                ))}
              </Flex>
            </Alert>
          ))}
        <Flex gap="sm" align="end" wrap>
          <Button variant="outline" loading={busy === 'validate'} disabled={busy != null} onClick={validate}>
            {messages.validate}
          </Button>
          <Field label={messages.publishNote}>
            <TextField _value={note} />
          </Field>
          <Button loading={busy === 'publish'} disabled={busy != null} onClick={publish}>
            {messages.publish}
          </Button>
        </Flex>
      </Flex>
    </Card>
  );
});
