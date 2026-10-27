/**
 * 流程定义的编辑（管理端）：两种写法，改的是同一份 0005 的定义——
 *   - 设计：流程设计器 FlowDesigner（画布、属性面板，边画边按模型包的规则校验）；
 *   - JSON：在代码编辑器里直接改，设计器没做成面板的设置（提醒、通知、泳道……）在这里写。
 * 换写法时互相转：设计换到 JSON 时按当前的定义排版；JSON 换回设计时要是合法的 JSON。"校验"交给后端列出错误与警告（不发布），
 * "发布"定一个新版本。打开已有的流程时载入最新版本，也能载入以前的某个版本再改；新建时给一个最小的骨架。
 * 改了停一会儿自动存成草稿（design-draft.ts），再打开时接着草稿改，可以放弃草稿；发布后草稿就没了。
 * "对比"列出正在改的与所基于的已发布版本差在哪（布局不算）。
 *
 *   <FlowDefinitionEditor processKey={key} people={peopleSource} onPublished={(result) => toaster.success(…)} />
 */
import { DEFINITION_SCHEMA, diffProcess, parseDefinition, type ProcessDefinition, type ProcessDiff } from '@zerodep-dev/flow-model';
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
import { useFlowClient } from '../../client/flow-client';
import type { DefinitionProblem, PublishedDefinition } from '../../client/types';
import { useDesignerMessages, type FlowDesignerMessages } from '../../lang/process-designer-messages';
import { useFlowMessages } from '../../lang/messages';
import { DefinitionDiffView } from './DefinitionDiffView';
import { DraftBanner } from './DraftBanner';
import { useDesignDraft } from './design-draft';
import { defaultOutcomes } from '../process-designer/editing';
import { FlowDesigner } from '../process-designer/FlowDesigner';

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

/** 新建时的骨架：一个人工节点，同意就结束，不同意直接结束流程 */
function skeleton(messages: FlowDesignerMessages): ProcessDefinition {
  const texts = messages.skeleton;
  return {
    schema: DEFINITION_SCHEMA,
    key: 'new-process',
    name: texts.process,
    nodes: [
      { id: 'start', type: 'start', name: texts.start },
      { id: 'approve', type: 'task', name: texts.task, assignees: [{ initiator: true }], outcomes: defaultOutcomes({ approve: messages.approveOutcome, reject: messages.rejectOutcome }) },
      { id: 'end', type: 'end', name: texts.end, result: 'approved' },
    ],
    edges: [
      { id: 'submit', from: 'start', to: 'approve' },
      { id: 'approved', from: 'approve', to: 'end', outcome: 'approve' },
    ],
  };
}

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
  const blank = skeleton(designerMessages);
  let definition = _state.raw<ProcessDefinition>(blank);
  let text = _state(pretty(blank));
  let loaded = _state(!processKey);
  let note = _state('');
  let problems = _state<readonly DefinitionProblem[] | null>(null);
  let diff = _state.raw<{ version: number; result: ProcessDiff } | null>(null);
  let failure = _state<string | null>(null);
  let notice = _state<string | null>(null);
  let busy = _state<string | null>(null);
  /** 编辑器里是哪个已发布的版本（或基于它的草稿）；新建时为 null */
  let baseVersion = _state<number | null>(null);
  /** 打开时接着的草稿是谁、什么时候存的 */
  let openedDraft = _state<{ by: string | null; at: Date } | null>(null);

  /** 现在写的这一份的 JSON 文字：设计时按定义排版，JSON 时就是编辑器里的字 */
  const currentJson = () => (mode === 'design' ? pretty(definition) : text);

  const draft = useDesignDraft({
    kind: 'process',
    key: () => processKey ?? (definition.key || null),
    name: () => definition.name ?? '',
    content: currentJson,
    baseVersion: () => baseVersion,
  });

  // 改了就不再是上次校验、对比的那一份：结果收起来
  watch(
    () => [definition, text],
    () => {
      problems = null;
      diff = null;
    },
  );

  const versions = useRequest((key: string | null, signal) => (key ? flow.versions(key, signal) : Promise.resolve([])), {
    params: () => processKey ?? null,
  });

  /** 编辑器换上一份 JSON：不是合法的定义 JSON 时只能在 JSON 里改 */
  function show(json: string) {
    try {
      definition = parseDefinition(json);
      text = pretty(definition);
    } catch {
      text = json;
      mode = 'json';
    }
  }

  /**
   * 载入一个版本（不给时最新的）。打开时（不给版本）有草稿就接着草稿改；挑了某个版本时载入那个版本，改了存成新的草稿
   */
  async function load(version?: number, withDraft = false) {
    if (!processKey) {
      draft.ready();
      return;
    }
    failure = null;
    problems = null;
    try {
      show((await flow.definitionJson(processKey, version)).json);
      baseVersion = version ?? versions.data?.[0]?.version ?? null;
      const opened = withDraft ? await draft.open() : null;
      if (opened?.content) {
        show(opened.content);
        baseVersion = opened.baseVersion;
        openedDraft = { by: opened.updatedByName, at: opened.updatedAt.toDate() };
      }
      loaded = true;
      draft.ready();
    } catch (error) {
      failure = errorText(error);
    }
  }
  void load(undefined, true);

  /** 放弃草稿：回到最新发布的版本 */
  async function discardDraft() {
    await draft.discard();
    openedDraft = null;
    await load();
  }

  /** 别人先存了草稿：载入他的，接着改 */
  async function reloadDraft() {
    const latest = await draft.reload();
    if (latest?.content) show(latest.content);
    draft.ready();
  }

  /** 换写法：设计 → JSON 按定义排版；JSON → 设计要读得出 JSON，读不出时留在 JSON 并提示 */
  function switchMode(next: Mode) {
    if (next === mode) return;
    if (next === 'json') {
      text = pretty(definition);
    } else {
      try {
        definition = parseDefinition(text);
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
      draft.published();
      openedDraft = null;
      baseVersion = result.version;
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

  /** 和所基于的已发布版本（新建的没有）对比 */
  async function compare() {
    const version = baseVersion ?? versions.data?.[0]?.version;
    if (!processKey || version == null) return;
    busy = 'compare';
    failure = null;
    try {
      const published = parseDefinition((await flow.definitionJson(processKey, version)).json);
      diff = { version, result: diffProcess(published, parseDefinition(currentJson())) };
    } catch (error) {
      failure = error instanceof SyntaxError ? messages.jsonInvalid(error.message) : errorText(error);
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
        <DraftBanner opened={openedDraft} status={draft.status} onDiscard={discardDraft} onReload={reloadDraft} />
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
        {diff && <DefinitionDiffView version={diff.version} diff={diff.result} />}
        <Flex gap="sm" align="end" wrap>
          <Button variant="outline" loading={busy === 'validate'} disabled={busy != null} onClick={validate}>
            {messages.validate}
          </Button>
          {processKey && (
            <Button variant="outline" loading={busy === 'compare'} disabled={busy != null} onClick={compare}>
              {messages.compare}
            </Button>
          )}
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
