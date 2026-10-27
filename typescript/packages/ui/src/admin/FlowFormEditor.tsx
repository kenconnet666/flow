/**
 * 表单的编辑（管理端），三种写法：
 *   - 字段清单：只写 0004 的字段清单 JSON，表单按清单自动排版；
 *   - 设计：表单设计器（FormDesigner）里拖组件、改属性，改的是下面那份代码；
 *   - 代码：写表单的 TSX（defineFields + defineFlowForm），字段清单从代码里取出（不执行代码）。
 * 设计与代码是同一份源码、同一个代码编辑器（设计时藏起来但不卸载），撤销重做只有代码编辑器的一个历史（0004 的 3.1）。
 * 边改边看：清单的问题（字段名、类型、属性……）随改随列出，没问题时右边的预览跟着变；发布定一个新版本。
 * 打开已有的表单时载入最新版本，也能载入以前的某个版本；新建时给一个最小的骨架。发布时只存源码，审批页在浏览器里编译。
 *
 *   <FlowFormEditor formKey={key} onPublished={(result) => toaster.success(…)} />
 */
import { validateManifest, type FormManifest } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import {
  Alert,
  Button,
  Card,
  Field,
  Flex,
  Grid,
  Select,
  Text,
  TextField,
  ToggleGroup,
  ToggleItem,
  useErrorMessage,
  useRequest,
  type PeopleSource,
} from '@zerodep-dev/vue-tsx-pro-ui';
import { CodeEditor, type CodeEditorHandle } from '@zerodep-dev/vue-tsx-pro-ui/code-editor';
import { watch } from 'vue';
import { useFlowClient } from '../client/flow-client';
import type { PublishedForm } from '../client/types';
import { applyEdits } from '../form-tools/design/edit';
import { FlowForm } from '../form/FlowForm';
import { FormDesigner } from './form-designer/FormDesigner';
import { useFlowMessages } from '../lang/messages';

/** FlowFormEditor 的属性 */
export interface FlowFormEditorProps {
  /** 要编辑的表单；新建时不给 */
  formKey?: string;
  /** 预览里人员字段的数据来源 */
  people?: PeopleSource;
  /** 发布成功 */
  onPublished?: (result: PublishedForm) => void;
}

/** 写法：字段清单（自动排版）、设计或代码 */
type Mode = 'manifest' | 'design' | 'code';

/** 新建时的骨架：一个必填的标题 */
const SKELETON: FormManifest = { title: { label: '标题', type: 'text', required: true } };

/** 当前写的东西分析出来的样子：取得出清单、没有问题时才预览、才能发布 */
interface Analysis {
  readonly manifest: FormManifest | null;
  readonly problems: readonly string[];
}

/** 改完停一会儿再分析、刷新预览（毫秒） */
const SETTLE = 400;

/** 表单的编辑 */
export const FlowFormEditor = _component(({ formKey, people, onPublished }: FlowFormEditorProps) => {
  const flow = useFlowClient();
  const messages = useFlowMessages();
  const errorText = useErrorMessage();
  let key = _state(formKey ?? '');
  let name = _state(formKey ? '' : messages.newForm);
  let category = _state('');
  let mode = _state<Mode>('manifest');
  let manifestText = _state(formKey ? '' : JSON.stringify(SKELETON, null, 2));
  let source = _state('');
  let note = _state('');
  let analysis = _state<Analysis>({ manifest: null, problems: [] });
  let failure = _state<string | null>(null);
  let notice = _state<string | null>(null);
  let busy = _state<string | null>(null);
  let editor: CodeEditorHandle | undefined;

  const versions = useRequest((k: string | null, signal) => (k ? flow.formVersions(k, signal) : Promise.resolve([])), {
    params: () => formKey ?? null,
  });

  /** 分析当前写的东西：读出清单（JSON 或从代码里取）再按模型包的规则校验 */
  async function analyze(): Promise<Analysis> {
    let manifest: FormManifest;
    if (mode === 'manifest') {
      try {
        manifest = JSON.parse(manifestText) as FormManifest;
      } catch (error) {
        return { manifest: null, problems: [messages.jsonInvalid((error as Error).message)] };
      }
    } else {
      // 取清单要解析 TSX（Babel），用到时才加载（设计、代码两种写法都是）
      const { extractFields } = await import('../form-tools');
      try {
        const result = extractFields(source);
        if (!result.manifest) return { manifest: null, problems: result.problems.map((p) => `${messages.extractProblem(p.code)}（${p.line}:${p.column}）`) };
        manifest = result.manifest as FormManifest;
      } catch (error) {
        return { manifest: null, problems: [messages.syntaxError((error as Error).message)] };
      }
    }
    const problems = validateManifest(manifest).map((p) => `${messages.problem(p.code, toTexts(p.params))}${p.field ? `（${p.field}）` : ''}`);
    return { manifest: problems.length ? null : manifest, problems };
  }

  let timer: ReturnType<typeof setTimeout> | undefined;
  watch(
    () => [mode, manifestText, source] as const,
    () => {
      clearTimeout(timer);
      timer = setTimeout(async () => (analysis = await analyze()), SETTLE);
    },
    { immediate: true },
  );

  /** 载入一个版本（不给时最新的） */
  async function load(version?: number) {
    if (!formKey) return;
    failure = null;
    try {
      const doc = await flow.formDocument(formKey, version);
      name = doc.name;
      category = doc.category ?? '';
      manifestText = JSON.stringify(JSON.parse(doc.manifest), null, 2);
      source = doc.clientSource ?? '';
      mode = doc.clientSource ? 'design' : 'manifest';
    } catch (error) {
      failure = errorText(error);
    }
  }
  void load();

  /** 换写法：清单换成设计、代码时按清单生成一份；代码换回清单时用代码里取出的清单（代码丢掉） */
  async function switchMode(next: Mode) {
    if (next === mode) return;
    if (next !== 'manifest' && !source.trim()) {
      const parsed = analysis.manifest ?? SKELETON;
      const { codeFromManifest } = await import('../form-tools');
      source = codeFromManifest(parsed);
    }
    if (next === 'manifest' && analysis.manifest) manifestText = JSON.stringify(analysis.manifest, null, 2);
    mode = next;
  }

  async function publish() {
    const current = await analyze();
    analysis = current;
    if (!current.manifest || !key.trim() || !name.trim()) return;
    busy = 'publish';
    failure = null;
    notice = null;
    try {
      const result = await flow.publishForm({
        key: key.trim(),
        name: name.trim(),
        ...(category.trim() ? { category: category.trim() } : {}),
        manifest: current.manifest,
        ...(mode !== 'manifest' ? { clientSource: source } : {}),
        ...(note.trim() ? { note: note.trim() } : {}),
      });
      note = '';
      notice = messages.published(result.version);
      await versions.refresh();
      onPublished?.(result);
    } catch (error) {
      failure = errorText(error);
    } finally {
      busy = null;
    }
  }

  return () => (
    <Card header={formKey ? `${messages.formEditor}：${formKey}` : messages.newForm}>
      <Flex direction="column" gap="md">
        {failure && <Alert tone="danger" onClose={() => (failure = null)}>{failure}</Alert>}
        {notice && <Alert tone="success" onClose={() => (notice = null)}>{notice}</Alert>}
        <Flex gap="md" wrap align="end">
          <Field label={messages.columnKey} required>
            <TextField _value={key} disabled={formKey != null} />
          </Field>
          <Field label={messages.columnTitle} required>
            <TextField _value={name} />
          </Field>
          <Field label={messages.columnCategory}>
            <TextField _value={category} />
          </Field>
          {formKey && (versions.data?.length ?? 0) > 0 && (
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
        <ToggleGroup aria-label={messages.formMode} required _value={mode} onUpdate:_value={(next: string) => void switchMode(next as Mode)}>
          <ToggleItem value="manifest">{messages.formModeManifest}</ToggleItem>
          <ToggleItem value="design">{messages.formModeDesign}</ToggleItem>
          <ToggleItem value="code">{messages.formModeCode}</ToggleItem>
        </ToggleGroup>
        {mode === 'design' && (
          <FormDesigner
            source={source}
            onEdit={(edits, merge) => (editor ? editor.edit(edits, { merge }) : (source = applyEdits(source, edits)))}
            onUndo={() => editor?.undo()}
            onRedo={() => editor?.redo()}
            {...(people ? { people } : {})}
          />
        )}
        {/* 设计时代码编辑器藏起来但不卸载：设计操作派发进它，撤销历史只有它一个 */}
        <Grid minItemWidth="24rem" gap="md" style={mode === 'design' ? { display: 'none' } : undefined}>
          {mode === 'manifest' ? (
            <CodeEditor key="manifest" _value={manifestText} aria-label={messages.formModeManifest} style={{ height: '30rem' }} />
          ) : (
            <CodeEditor key="code" _value={source} aria-label={messages.formModeCode} style={{ height: '30rem' }} onReady={(handle) => (editor = handle)} />
          )}
          <Card header={messages.formPreview}>
            {analysis.manifest ? (
              <FlowForm
                view={{
                  manifest: analysis.manifest,
                  values: {},
                  access: { default: 'editable' },
                  start: true,
                  compiled: null,
                  source: mode === 'manifest' ? null : source,
                }}
                {...(people ? { people } : {})}
              />
            ) : (
              <Text tone="muted">{messages.formPreviewBlocked}</Text>
            )}
          </Card>
        </Grid>
        {analysis.problems.length > 0 && (
          <Alert tone="danger">
            <Flex direction="column" gap="xs">
              {analysis.problems.map((p, i) => (
                <Text key={i}>✕ {p}</Text>
              ))}
            </Flex>
          </Alert>
        )}
        <Flex gap="sm" align="end" wrap>
          <Field label={messages.publishNote}>
            <TextField _value={note} />
          </Field>
          <Button
            loading={busy === 'publish'}
            disabled={busy != null || !analysis.manifest || !key.trim() || !name.trim()}
            onClick={publish}
          >
            {messages.publish}
          </Button>
        </Flex>
      </Flex>
    </Card>
  );
});

/** 问题的参数都写成文字（模型包的参数可能是数） */
const toTexts = (params: Readonly<Record<string, string | number>> | undefined): Record<string, string> =>
  Object.fromEntries(Object.entries(params ?? {}).map(([k, v]) => [k, String(v)]));
