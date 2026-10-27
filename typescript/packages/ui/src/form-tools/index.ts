/**
 * 表单工具（子路径 `@zerodep-dev/flow-ui/form-tools`，0004 的 1.4、2、3）：不依赖 Vue，处理表单源码——
 * 从源码取出字段清单、按清单生成一份代码表单，以及表单设计器的解析与写回（源码 ↔ 字段列表与组件树）。
 */
export { codeFromManifest } from './code-from-manifest';
export { compileFormSource, type CompiledForm } from './compile';
export { extractFields, type ExtractProblem, type ExtractResult } from './extract';
export {
  allNodes,
  findNode,
  parentOf,
  parseDesign,
  type DesignNode,
  type ElementInfo,
  type FieldEntry,
  type FormDesign,
  type PropInfo,
  type PropValue,
  type SourceRange,
} from './design/parse';
export {
  addField,
  applyEdits,
  designSource,
  ensureImport,
  insertNode,
  moveNode,
  removeField,
  removeNode,
  renameField,
  replaceTag,
  setField,
  setProp,
  setText,
  type TextEdit,
} from './design/edit';
export { printComponent, printField } from './design/print';
export {
  controlsFor,
  findFormComponent,
  isRegisteredFieldType,
  formComponents,
  registerFormComponent,
  FORM_MODULE,
  UI_MODULE,
  type FormComponent,
  type FormComponentProp,
  type PropEditor,
} from './design/registry';
