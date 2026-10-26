/**
 * 表单工具（子路径 `@zerodep-dev/flow-ui/form-tools`，0004 的 1.4、7.3）：不依赖 Vue，处理表单源码——
 * 从源码取出字段清单、按清单生成一份代码表单；写回清单（设计器用）随表单设计器补上。
 */
export { codeFromManifest } from './code-from-manifest';
export { extractFields, type ExtractProblem, type ExtractResult } from './extract';
