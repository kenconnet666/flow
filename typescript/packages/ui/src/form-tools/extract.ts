/**
 * 从表单源码里取出字段清单（0004 的 1.2、1.4）：找到 `export const fields = defineFields({ … })`，把参数按字面量读成 JSON。
 * 不执行代码；参数必须是 JSON5 值（对象、数组、文字、数字、true / false / null，可以有注释、尾逗号、不加引号的键），
 * 写了变量、函数调用、展开、计算的键、模板字符串、正则、undefined 时报问题，指出行列。
 *
 *   const { manifest, problems } = extractFields(source);
 *   if (problems.length) showProblems(problems);   // 保存时报，不发布
 *
 * 只依赖 Babel 的解析器（不依赖 Vue）：设计器、发布页、Node 里的脚本、TS 后端都能用。
 */
import { parse } from '@babel/parser';
import type { Expression, Node, ObjectExpression, Program } from '@babel/types';

/** 清单里一处不合规的写法 */
export interface ExtractProblem {
  /** fields-missing：没有导出 fields；fields-not-define：不是 defineFields(...)；not-literal：参数里有不是字面量的写法 */
  readonly code: 'fields-missing' | 'fields-not-define' | 'not-literal';
  /** 说明（英文，给开发者看的） */
  readonly message: string;
  /** 行，从 1 开始 */
  readonly line: number;
  /** 列，从 1 开始 */
  readonly column: number;
}

/** 取出的结果：清单（取不出时为 null）与问题 */
export interface ExtractResult {
  readonly manifest: Record<string, unknown> | null;
  readonly problems: readonly ExtractProblem[];
}

/** 从源码里取出字段清单；语法错误时抛出 Babel 的错误（带行列） */
export function extractFields(source: string): ExtractResult {
  const program = parse(source, { sourceType: 'module', plugins: ['typescript', 'jsx'] }).program;
  const call = findFieldsCall(program);
  if ('problem' in call) return { manifest: null, problems: [call.problem] };
  const problems: ExtractProblem[] = [];
  const manifest = literal(call.argument, problems);
  return problems.length ? { manifest: null, problems } : { manifest: manifest as Record<string, unknown>, problems };
}

/** `export const fields = defineFields(参数)` 的参数 */
export function findFieldsCall(program: Program): { argument: ObjectExpression } | { problem: ExtractProblem } {
  for (const statement of program.body) {
    if (statement.type !== 'ExportNamedDeclaration' || statement.declaration?.type !== 'VariableDeclaration') continue;
    for (const declarator of statement.declaration.declarations) {
      if (declarator.id.type !== 'Identifier' || declarator.id.name !== 'fields') continue;
      const init = unwrap(declarator.init);
      const isDefine =
        init?.type === 'CallExpression' && init.callee.type === 'Identifier' && init.callee.name === 'defineFields';
      const argument = isDefine ? unwrap(init.arguments[0] as Expression | undefined) : undefined;
      if (!argument || argument.type !== 'ObjectExpression') {
        return { problem: problem('fields-not-define', 'fields must be defineFields({ … }) with an object literal', declarator) };
      }
      return { argument };
    }
  }
  return { problem: { code: 'fields-missing', message: 'the form code does not export fields', line: 1, column: 1 } };
}

/** 去掉 TS 的 `as const`、`satisfies …` 与括号 */
function unwrap(node: Expression | null | undefined): Expression | null | undefined {
  let current = node;
  while (current && (current.type === 'TSAsExpression' || current.type === 'TSSatisfiesExpression' || current.type === 'ParenthesizedExpression')) {
    current = current.expression;
  }
  return current;
}

/** 字面量 → JSON 值；遇到不是字面量的写法记一条问题、返回 null */
export function literal(node: Node, problems: ExtractProblem[]): unknown {
  switch (node.type) {
    case 'StringLiteral':
    case 'NumericLiteral':
    case 'BooleanLiteral':
      return node.value;
    case 'NullLiteral':
      return null;
    case 'UnaryExpression':
      if (node.operator === '-' && node.argument.type === 'NumericLiteral') return -node.argument.value;
      break;
    case 'TemplateLiteral':
      // 没有插值的模板字符串也不算：规则是"JSON5 值"，一句话说得清
      break;
    case 'ArrayExpression':
      return node.elements.map((element) => (element && element.type !== 'SpreadElement' ? literal(element, problems) : invalid(element ?? node, problems)));
    case 'ObjectExpression': {
      const out: Record<string, unknown> = {};
      for (const property of node.properties) {
        if (property.type !== 'ObjectProperty' || property.computed) {
          invalid(property, problems);
          continue;
        }
        const key = property.key.type === 'Identifier' ? property.key.name : property.key.type === 'StringLiteral' ? property.key.value : null;
        if (key === null) invalid(property.key, problems);
        else out[key] = literal(property.value, problems);
      }
      return out;
    }
    case 'TSAsExpression':
    case 'TSSatisfiesExpression':
    case 'ParenthesizedExpression':
      return literal(node.expression, problems);
  }
  return invalid(node, problems);
}

function invalid(node: Node, problems: ExtractProblem[]): null {
  problems.push(problem('not-literal', `only JSON5 values may appear in defineFields(…), found ${node.type}`, node));
  return null;
}

function problem(code: ExtractProblem['code'], message: string, node: Node): ExtractProblem {
  return { code, message, line: node.loc?.start.line ?? 1, column: (node.loc?.start.column ?? 0) + 1 };
}
