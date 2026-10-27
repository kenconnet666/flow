/**
 * 表单容器（0004 的 4.4、5.4）：按引擎给的表单视图（FormView）渲染一张表单，并把"校验、取值"交给页面（onReady）。
 *
 *   <FlowForm view={workItem.form} people={peopleSource} onReady={(form) => (handle = form)} />
 *   // 审批面板点按钮时：if (await handle.validate()) flow.complete({ …, form: handle.values() })
 *
 * 用哪份代码：
 *   1. 有编译好的代码：直接执行（审批页面不带编译器）；
 *   2. 只有源码：在浏览器里编译（加载 Babel；应用的 Vite 配置要加组件库的 browserCompile() 插件）；
 *   3. 都没有：按字段清单自动排版（autoFlowForm）。
 * 表单代码能导入的模块见 flowFormModules；要用别的（业务组件、图标）时经 modules 加进去。代码导出的 fields 与引擎存的清单不一致时
 * 不渲染、报错（0004 的 1.2 兜底：绕过保存流程、或提取出了错）。
 */
import type { FormManifest } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Skeleton, type PeopleSource } from '@zerodep-dev/vue-tsx-pro-ui';
import { h, watch, type Component } from 'vue';
import type { FormView } from '../client/types';
import { useFlowMessages } from '../lang/messages';
import { autoFlowForm } from './auto-form';
import type { FlowFormContext, FlowFormHandle } from './context';

/** FlowForm 的属性 */
export interface FlowFormProps {
  /** 引擎给的表单视图（发起页、待办详情里的 form） */
  view: Pick<FormView, 'manifest' | 'values' | 'access' | 'start' | 'compiled' | 'source'>;
  /** fill 按权限填写（默认）；view 全部只读 */
  mode?: 'fill' | 'view';
  /** 人员字段的数据来源 */
  people?: PeopleSource;
  /** 表单代码还能导入的模块：import 写的名字 → 模块本身（`import * as` 拿到的对象），并进 flowFormModules */
  modules?: Readonly<Record<string, unknown>>;
  /** 取 `sys.`、流程变量（显示条件里用）；不给时这些都是空的 */
  read?: (name: string) => unknown;
  /** 表单建好了：拿到校验、取值的操作 */
  onReady?: (form: FlowFormHandle) => void;
  /** 换代码时先留着旧的表单，新的编好了再换上（表单设计器：每次改动都重新编译，画布不闪） */
  keepPrevious?: boolean;
}

/** 表单代码默认能导入的模块：Vue、组件库、flow-ui 的表单写法。用到时才加载 */
export async function flowFormModules(): Promise<Record<string, unknown>> {
  const [vue, tsx, styles, ui, form] = await Promise.all([
    import('vue'),
    import('@zerodep-dev/vue-tsx-pro-tsx'),
    import('@zerodep-dev/vue-tsx-pro-tsx/styles'),
    import('@zerodep-dev/vue-tsx-pro-ui'),
    import('./index'),
  ]);
  return {
    vue,
    '@zerodep-dev/vue-tsx-pro-tsx': tsx,
    '@zerodep-dev/vue-tsx-pro-tsx/styles': styles,
    '@zerodep-dev/vue-tsx-pro-ui': ui,
    '@zerodep-dev/flow-ui/form': form,
  };
}

/** 表单容器 */
export const FlowForm = _component(({ view, mode = 'fill', people, modules, read, onReady, keepPrevious }: FlowFormProps) => {
  const messages = useFlowMessages();
  let component = _state.raw<Component | null>(null);
  let failure = _state<string | null>(null);

  async function load(): Promise<Component> {
    if (!view.compiled && !view.source) return autoFlowForm(view.manifest);
    const { compileInThread, evaluateModule } = await import('@zerodep-dev/vue-tsx-pro-tsx/browser-compile');
    const code = view.compiled ?? (await compileInThread(view.source!, { filename: 'Form.tsx' }));
    const exported = evaluateModule<{ default?: Component; fields?: FormManifest }>(code, {
      ...(await flowFormModules()),
      ...modules,
    });
    if (!exported.default) throw new Error('the form code has no default export');
    if (exported.fields && JSON.stringify(exported.fields) !== JSON.stringify(view.manifest)) {
      throw new Error('the fields exported by the form code differ from the published manifest');
    }
    return exported.default;
  }

  // 换了表单（另一个待办）重新加载；同一张表单只加载一次。连着换了几次时只用最后一次的结果
  let loads = 0;
  watch(
    () => [view.compiled, view.source, view.manifest] as const,
    async () => {
      const current = ++loads;
      if (!keepPrevious) component = null;
      try {
        const next = await load();
        if (current !== loads) return;
        failure = null;
        component = next;
      } catch (error) {
        if (current === loads) failure = error instanceof Error ? error.message : String(error);
      }
    },
    { immediate: true },
  );

  const context = (): FlowFormContext => ({
    manifest: view.manifest,
    values: view.values,
    nodeAccess: view.access,
    start: view.start,
    mode,
    people,
    read: read ?? (() => undefined),
    attach: (handle) => onReady?.(handle),
  });

  return () => {
    if (failure) return <Alert tone="danger" title={messages.formFailed}>{failure}</Alert>;
    if (!component) return <Skeleton />;
    return h(component, { context: context() });
  };
});
