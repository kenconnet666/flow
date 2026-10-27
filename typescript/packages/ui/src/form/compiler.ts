/**
 * 代码表单的编译放在哪：接入方给了编译 Worker 时在 Worker 里编（表单设计器每改一下都要重新编译，界面不卡），
 * 没给时在当前线程编（第一次编译时加载 Babel）。Worker 要由应用的 Vite 打包（`?worker`），所以由接入方建好交进来：
 *
 *   import { createCompileClient } from '@zerodep-dev/vue-tsx-pro-tsx/browser-compile';
 *   import CompileWorker from '@zerodep-dev/vue-tsx-pro-tsx/browser-compile/worker?worker';
 *   provideFormCompiler(createCompileClient(() => new CompileWorker()));   // 根组件里
 */
import type { CompileClient } from '@zerodep-dev/vue-tsx-pro-tsx/browser-compile';
import { inject, provide, type InjectionKey } from 'vue';

const FORM_COMPILER: InjectionKey<CompileClient> = Symbol('flow-form-compiler');

/** 在根组件（或某一块页面）里给编译 Worker */
export function provideFormCompiler(client: CompileClient): void {
  provide(FORM_COMPILER, client);
}

/** 给了的编译 Worker；没给时为 undefined */
export function useFormCompiler(): CompileClient | undefined {
  return inject(FORM_COMPILER, undefined);
}

/** 编译表单的前端源码，返回能执行的代码；语法错误时抛出（Worker 里编时错误消息带代码帧） */
export async function compileForm(source: string, compiler: CompileClient | undefined): Promise<string> {
  if (compiler) {
    const result = await compiler.compile(source, { filename: 'Form.tsx' });
    if ('error' in result) throw new Error(result.error);
    return result.code;
  }
  const { compileInThread } = await import('@zerodep-dev/vue-tsx-pro-tsx/browser-compile');
  return compileInThread(source, { filename: 'Form.tsx' });
}
