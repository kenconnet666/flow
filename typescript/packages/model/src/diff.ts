/**
 * 两个版本的流程定义差在哪（0002 §1 的版本对比、frontend.md §3.2）：哪些节点、连线、泳道加了、删了、改了，改了哪些属性。
 * 与 Java 的 `ProcessDiff` 同一套规则，两边跑 spec/diff 的样例。比较的是 JSON：对象的键顺序不算差别，数字按值比。
 * 布局（`layout`）默认不比，设计器拖了位置不算改动；要比时传 `{ layout: true }`。版本号（`version`）不比。
 *
 *   diffProcess(v1, v2);
 *   // → { process: ['name'], nodes: { added: ['audit'], removed: [], changed: [{ id: 'review', keys: ['outcomes'] }] }, ... }
 */
import type { ProcessDefinition } from './definition.js';
import { sameValue } from './values.js';

/** 一类东西（节点、连线、泳道）的差别 */
export interface ItemDiff {
  /** 新版本里才有的，按新版本里的先后 */
  readonly added: readonly string[];
  /** 旧版本里才有的，按旧版本里的先后 */
  readonly removed: readonly string[];
  /** 两边都有但不一样的，按新版本里的先后；keys 是不一样的属性，按名字排序 */
  readonly changed: readonly { readonly id: string; readonly keys: readonly string[] }[];
}

/** 两个版本的差别 */
export interface ProcessDiff {
  /** 流程级不一样的属性（节点、连线、泳道、布局、版本号以外），按名字排序 */
  readonly process: readonly string[];
  readonly lanes: ItemDiff;
  readonly nodes: ItemDiff;
  readonly edges: ItemDiff;
  /** 只在要求比布局时有：位置变了的节点、拐点变了的连线 */
  readonly layout?: { readonly nodes: readonly string[]; readonly edges: readonly string[] };
}

type Json = unknown;

/** 两个 JSON 值相等：对象不看键的顺序，数字按值比，null 与没有这个键相同（Java 读出来分不开） */
export function sameJson(a: Json, b: Json): boolean {
  if (a == null || b == null) return a == null && b == null;
  if (Array.isArray(a) || Array.isArray(b)) {
    return Array.isArray(a) && Array.isArray(b) && a.length === b.length && a.every((item, i) => sameJson(item, b[i]));
  }
  if (isObject(a) || isObject(b)) {
    if (!isObject(a) || !isObject(b)) return false;
    const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
    return [...keys].every((key) => sameJson(a[key], b[key]));
  }
  if (typeof a === 'number' && typeof b === 'number') return sameValue(a, b);
  return a === b;
}

const isObject = (value: Json): value is Record<string, Json> => typeof value === 'object' && value !== null && !Array.isArray(value);

/** 两个对象里不一样的属性（undefined 与没有这个属性视为相同），按名字排序 */
function changedKeys(a: Record<string, Json>, b: Record<string, Json>, skip: ReadonlySet<string>): string[] {
  const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
  return [...keys].filter((key) => !skip.has(key) && !sameJson(a[key], b[key])).sort();
}

function diffItems(before: readonly { id: string }[], after: readonly { id: string }[]): ItemDiff {
  const byId = (items: readonly { id: string }[]) => {
    const map = new Map<string, Record<string, Json>>();
    for (const item of items) if (!map.has(item.id)) map.set(item.id, item as unknown as Record<string, Json>);
    return map;
  };
  const a = byId(before);
  const b = byId(after);
  const none = new Set<string>(['id']);
  return {
    added: [...b.keys()].filter((id) => !a.has(id)),
    removed: [...a.keys()].filter((id) => !b.has(id)),
    changed: [...b.keys()]
      .filter((id) => a.has(id))
      .map((id) => ({ id, keys: changedKeys(a.get(id)!, b.get(id)!, none) }))
      .filter((change) => change.keys.length > 0),
  };
}

const PROCESS_SKIP: ReadonlySet<string> = new Set(['nodes', 'edges', 'lanes', 'layout', 'version']);

/** 两个版本的差别 */
export function diffProcess(before: ProcessDefinition, after: ProcessDefinition, options: { layout?: boolean } = {}): ProcessDiff {
  const result: ProcessDiff = {
    process: changedKeys(before as unknown as Record<string, Json>, after as unknown as Record<string, Json>, PROCESS_SKIP),
    lanes: diffItems(before.lanes ?? [], after.lanes ?? []),
    nodes: diffItems(before.nodes, after.nodes),
    edges: diffItems(before.edges, after.edges),
  };
  if (!options.layout) return result;
  const moved = (a: Readonly<Record<string, Json>> = {}, b: Readonly<Record<string, Json>> = {}) =>
    [...new Set([...Object.keys(a), ...Object.keys(b)])].filter((id) => !sameJson(a[id], b[id])).sort();
  return {
    ...result,
    layout: { nodes: moved(before.layout?.nodes, after.layout?.nodes), edges: moved(before.layout?.edges, after.layout?.edges) },
  };
}
