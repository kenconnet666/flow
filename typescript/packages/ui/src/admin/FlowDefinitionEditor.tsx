/**
 * 流程定义的编辑（管理端，设计器之前的写法）：在代码编辑器里改 0005 的定义 JSON，"校验"列出错误与警告（不发布），
 * "发布"定一个新版本。打开已有的流程时载入最新版本，也能载入以前的某个版本再改；新建时给一个最小的骨架。
 *
 *   <FlowDefinitionEditor processKey={key} onPublished={(result) => toaster.success(…)} />
 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import {
  Alert,
  Button,
  Card,
  Field,
  Flex,
  HttpError,
  Select,
  Text,
  TextField,
  useErrorMessage,
  useRequest,
} from '@zerodep-dev/vue-tsx-pro-ui';
import { CodeEditor } from '@zerodep-dev/vue-tsx-pro-ui/code-editor';
import { watch } from 'vue';
import { useFlowClient } from '../client/flow-client';
import type { DefinitionProblem, PublishedDefinition } from '../client/types';
import { useFlowMessages } from '../lang/messages';

/** FlowDefinitionEditor 的属性 */
export interface FlowDefinitionEditorProps {
  /** 要编辑的流程；新建时不给 */
  processKey?: string;
  /** 发布成功 */
  onPublished?: (result: PublishedDefinition) => void;
}

/** 新建时的骨架：一个人工节点，通过就结束 */
const SKELETON = {
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

/** 排版好的 JSON；不是合法 JSON 时原样 */
const pretty = (text: string) => {
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return text;
  }
};

/** 流程定义的编辑 */
export const FlowDefinitionEditor = _component(({ processKey, onPublished }: FlowDefinitionEditorProps) => {
  const flow = useFlowClient();
  const messages = useFlowMessages();
  const errorText = useErrorMessage();
  let text = _state(processKey ? '' : JSON.stringify(SKELETON, null, 2));
  let note = _state('');
  let problems = _state<readonly DefinitionProblem[] | null>(null);
  let failure = _state<string | null>(null);
  let notice = _state<string | null>(null);
  let busy = _state<string | null>(null);

  // 改了就不再是上次校验的那一份：校验结果收起来
  watch(
    () => text,
    () => (problems = null),
  );

  const versions = useRequest((key: string | null, signal) => (key ? flow.versions(key, signal) : Promise.resolve([])), {
    params: () => processKey ?? null,
  });

  /** 载入一个版本（不给时最新的）到编辑器 */
  async function load(version?: number) {
    if (!processKey) return;
    failure = null;
    problems = null;
    try {
      text = pretty((await flow.definitionJson(processKey, version)).json);
    } catch (error) {
      failure = errorText(error);
    }
  }
  void load();

  async function validate() {
    busy = 'validate';
    failure = null;
    notice = null;
    try {
      problems = (await flow.validateDefinition(text)).problems;
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
      const result = await flow.publishDefinition(text, note.trim() || undefined);
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
        <CodeEditor _value={text} aria-label={messages.definitionEditor} style={{ height: '28rem' }} />
        {problems && (
          problems.length === 0 ? (
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
          )
        )}
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
