/**
 * 流程定义 JSON 的格式版本（定义里的 `schema`，0005 第 1 节），同 Java 的 `DefinitionSchema`：已发布的版本存的是发布时的原样，
 * 读之前按 STEPS 一步步升到 DEFINITION_SCHEMA。不写 `schema` 的是第 1 版。
 *
 * 改定义格式（改名、换结构）时：DEFINITION_SCHEMA 加一，STEPS 末尾加一步"上一版 → 这一版"，Java 那边同样加一步，
 * 规格用例（spec/definitions）加一份旧格式的定义。只加字段、不改原有字段的意思时不用加版本。
 *
 *   const definition = parseDefinition(json);   // 旧格式升到当前格式；比当前还新时抛错
 */
import type { ProcessDefinition } from './definition.js';

/** 这一版的模型包写的格式 */
export const DEFINITION_SCHEMA = 1;

type Tree = Record<string, unknown>;

/** 第 i 个是第 i+1 版 → 第 i+2 版；现在只有第 1 版，没有要升的 */
const STEPS: readonly ((tree: Tree) => Tree)[] = [];

/** 定义写的是第几版：没写时是 1；写的不是正整数时抛错 */
export function definitionSchemaOf(tree: Readonly<Tree>): number {
  const schema = tree['schema'];
  if (schema === undefined) return 1;
  if (typeof schema !== 'number' || !Number.isInteger(schema) || schema < 1) throw new Error('$.schema: must be a positive integer');
  return schema;
}

/** 升到当前的格式：返回新的一份（`schema` 是 DEFINITION_SCHEMA），原来的不改；不是对象、比当前还新时抛错 */
export function upgradeDefinition(raw: unknown): ProcessDefinition {
  if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) throw new Error('$: a process definition must be an object');
  const version = definitionSchemaOf(raw as Tree);
  if (version > DEFINITION_SCHEMA) {
    throw new Error(`$.schema: format ${version} is newer than this version understands (${DEFINITION_SCHEMA}); upgrade flow`);
  }
  if (version === DEFINITION_SCHEMA) return raw as ProcessDefinition;
  const upgraded = STEPS.slice(version - 1).reduce((tree, step) => step(tree), { ...(raw as Tree) });
  return { ...upgraded, schema: DEFINITION_SCHEMA } as unknown as ProcessDefinition;
}

/** 读一段定义 JSON：JSON.parse 之后升到当前格式 */
export function parseDefinition(json: string): ProcessDefinition {
  return upgradeDefinition(JSON.parse(json));
}
