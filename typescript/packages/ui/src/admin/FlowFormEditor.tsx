/**
 * 表单的编辑（管理端），三种写法：
 *   - 字段清单：只写 0004 的字段清单 JSON，表单按清单自动排版；
 *   - 设计：表单设计器（FormDesigner）里拖组件、改属性，改的是下面那份代码；
 *   - 代码：写表单的 TSX（defineFields + defineFlowForm），字段清单从代码里取出（不执行代码）。
 * 设计与代码是同一份源码、同一个代码编辑器（设计时藏起来但不卸载），撤销重做只有代码编辑器的一个历史（0004 的 3.1）。
 * 改了停一会儿自动存成草稿（写法、清单、源码，design-draft.ts），再打开时接着草稿改；发布后草稿就没了。
 * "后端代码"页签写表单的后端代码（Java 的 FormHandler），能试编译，跟表单一起发布。
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
  HttpError,
  Link,
  Select,
  Tab,
  TabList,
  Tabs,
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
import type { CompileProblem, PublishedForm } from '../client/types';
import { applyEdits } from '../form-tools/design/edit';
import { FlowForm } from '../form/FlowForm';
import { DraftBanner } from './DraftBanner';
import { useDesignDraft } from './design-draft';
import { FormDesigner } from './form-designer/FormDesigner';
import { FormServerCode } from './FormServerCode';
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

/** 分析出的一个问题；代码里的问题带位置（行列从 1 起），点它跳到代码编辑器里 */
interface Problem {
  readonly text: string;
  readonly at?: { readonly line: number; readonly column: number };
}

/** 当前写的东西分析出来的样子：取得出清单、没有问题时才预览、才能发布 */
interface Analysis {
  readonly manifest: FormManifest | null;
  readonly problems: readonly Problem[];
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
  /** 后端代码（Java）；空的表示没有 */
  let serverSource = _state('');
  /** 发布时后端报出的编译问题 */
  let serverProblems = _state<readonly CompileProblem[] | null>(null);
  /** 看的是表单（清单、设计、代码），还是后端代码 */
  let panel = _state<'form' | 'server'>('form');
  let note = _state('');
  let analysis = _state<Analysis>({ manifest: null, problems: [] });
  let failure = _state<string | null>(null);
  let notice = _state<string | null>(null);
  let busy = _state<string | null>(null);
  let editor: CodeEditorHandle | undefined;
  /** 编辑器里是哪个已发布的版本（或基于它的草稿）；新建时为 null */
  let baseVersion = _state<number | null>(null);
  let openedDraft = _state<{ by: string | null; at: Date } | null>(null);

  /** 草稿里存的：写法、清单的文字、源码 */
  const draftContent = () => JSON.stringify({ mode, manifestText, source, serverSource });
  const draft = useDesignDraft({
    kind: 'form',
    key: () => (formKey ?? key.trim()) || null,
    name: () => name,
    content: draftContent,
    baseVersion: () => baseVersion,
  });

  /** 换上草稿里的内容 */
  function showDraft(content: string) {
    const saved = JSON.parse(content) as { mode: Mode; manifestText: string; source: string; serverSource?: string };
    mode = saved.mode;
    manifestText = saved.manifestText;
    source = saved.source;
    serverSource = saved.serverSource ?? '';
  }

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
        return { manifest: null, problems: [{ text: messages.jsonInvalid((error as Error).message) }] };
      }
    } else {
      // 取清单要解析 TSX（Babel），用到时才加载（设计、代码两种写法都是）
      const { extractFields } = await import('../form-tools');
      try {
        const result = extractFields(source);
        if (!result.manifest) {
          return {
            manifest: null,
            problems: result.problems.map((p) => ({ text: `${messages.extractProblem(p.code)}（${p.line}:${p.column}）`, at: { line: p.line, column: p.column } })),
          };
        }
        manifest = result.manifest as FormManifest;
      } catch (error) {
        // Babel 的语法错误带 loc（列从 0 起）
        const loc = (error as { loc?: { line: number; column: number } }).loc;
        return { manifest: null, problems: [{ text: messages.syntaxError((error as Error).message), ...(loc ? { at: { line: loc.line, column: loc.column + 1 } } : {}) }] };
      }
    }
    const problems = validateManifest(manifest).map((p) => ({ text: `${messages.problem(p.code, toTexts(p.params))}${p.field ? `（${p.field}）` : ''}` }));
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

  /** 载入一个版本（不给时最新的）；打开时（withDraft）有草稿就接着草稿改 */
  async function load(version?: number, withDraft = false) {
    if (!formKey) {
      draft.ready();
      return;
    }
    failure = null;
    try {
      const doc = await flow.formDocument(formKey, version);
      name = doc.name;
      category = doc.category ?? '';
      manifestText = JSON.stringify(JSON.parse(doc.manifest), null, 2);
      source = doc.clientSource ?? '';
      serverSource = doc.serverSource ?? '';
      mode = doc.clientSource ? 'design' : 'manifest';
      baseVersion = doc.version;
      const opened = withDraft ? await draft.open() : null;
      if (opened?.content) {
        showDraft(opened.content);
        baseVersion = opened.baseVersion;
        openedDraft = { by: opened.updatedByName, at: opened.updatedAt.toDate() };
      }
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
    if (latest?.content) showDraft(latest.content);
  }

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
      const compiled = mode !== 'manifest' ? await (await import('../form-tools')).compileFormSource(source) : null;
      const result = await flow.publishForm({
        key: key.trim(),
        name: name.trim(),
        ...(category.trim() ? { category: category.trim() } : {}),
        manifest: current.manifest,
        ...(compiled ? { clientSource: source, ...compiled } : {}),
        ...(serverSource.trim() ? { serverLanguage: 'java' as const, serverSource } : {}),
        ...(note.trim() ? { note: note.trim() } : {}),
      });
      note = '';
      notice = messages.published(result.version);
      draft.published();
      openedDraft = null;
      baseVersion = result.version;
      await versions.refresh();
      onPublished?.(result);
      serverProblems = null;
    } catch (error) {
      // 后端代码编不过：转到后端代码页签，问题带行列列出来
      const compile = error instanceof HttpError ? (error.errors ?? []).filter((d) => d.code === 'compile-error') : [];
      if (compile.length) {
        serverProblems = compile.map((d) => ({
          line: Number(d.params?.['line'] ?? 1),
          column: Number(d.params?.['column'] ?? 1),
          message: String(d.params?.['message'] ?? ''),
        }));
        panel = 'server';
      } else {
        failure = errorText(error);
      }
    } finally {
      busy = null;
    }
  }

  return () => (
    <Card header={formKey ? `${messages.formEditor}：${formKey}` : messages.newForm}>
      <Flex direction="column" gap="md">
        {failure && <Alert tone="danger" onClose={() => (failure = null)}>{failure}</Alert>}
        {notice && <Alert tone="success" onClose={() => (notice = null)}>{notice}</Alert>}
        <DraftBanner opened={openedDraft} status={draft.status} onDiscard={discardDraft} onReload={reloadDraft} />
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
        <Tabs _value={panel} onUpdate:_value={(next: string) => (panel = next as 'form' | 'server')}>
          <TabList aria-label={messages.formEditor}>
            <Tab value="form">{messages.formPanelForm}</Tab>
            <Tab value="server">{messages.formPanelServer}</Tab>
          </TabList>
        </Tabs>
        {panel === 'server' && <FormServerCode _value={serverSource} problems={serverProblems} />}
        {/* 看后端代码时表单这边藏起来但不卸载：代码编辑器的撤销历史留着 */}
        <Flex direction="column" gap="md" style={panel === 'server' ? { display: 'none' } : undefined}>
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
        </Flex>
        {analysis.problems.length > 0 && (
          <Alert tone="danger">
            <Flex direction="column" gap="xs">
              {analysis.problems.map((p, i) =>
                p.at && mode === 'code' ? (
                  <Link key={i} href="#" onClick={(event: MouseEvent) => (event.preventDefault(), editor?.goTo(p.at!.line, p.at!.column))}>
                    ✕ {p.text}
                  </Link>
                ) : (
                  <Text key={i}>✕ {p.text}</Text>
                ),
              )}
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
