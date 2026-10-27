/**
 * 发布前把表单的前端源码编译好（0004 的 5.4）：编译结果连同编译器版本一起存，审批页面版本一样时直接执行，不用再编译。
 * 表单编辑器发布、导入表单时用；编译要加载 Babel，只在这时加载。
 *
 *   const compiled = await compileFormSource(source, useFormCompiler());   // 给了编译 Worker 时在 Worker 里编
 *   await flow.publishForm({ …, clientSource: source, ...compiled });
 */

import type { CompileClient } from '@zerodep-dev/vue-tsx-pro-tsx/browser-compile';
import { compileForm } from '../form/compiler';

/** 编译结果与编译它的编译器版本：FormPublication 的两个字段 */
export interface CompiledForm {
  readonly clientCompiled: string;
  readonly compilerVersion: string;
}

/** 编译表单的前端源码；语法错误时抛出 */
export async function compileFormSource(source: string, compiler?: CompileClient): Promise<CompiledForm> {
  const { COMPILER_VERSION } = await import('@zerodep-dev/vue-tsx-pro-tsx/browser-compile');
  return { clientCompiled: await compileForm(source, compiler), compilerVersion: COMPILER_VERSION };
}
