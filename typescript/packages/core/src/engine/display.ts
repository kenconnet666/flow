/**
 * 实例在列表上显示的三样东西，每一步结束时按最新的表单与变量重算（同 Java 的 InstanceDisplay）：
 *   - 标题：定义的 title 模板，花括号里的引用换成值（`"采购申请：{form.title}"` → "采购申请：办公桌"）；
 *   - 摘要列：定义的 summary 里每个引用 → 值；
 *   - 正在处理的节点：还开着的待办在哪些节点上（去重），加上停着等的节点；实例结束了就是空的。
 */
import { toText, type CurrentNode, type ProcessDefinition } from '@zerodep-dev/flow-model';
import type { InstanceRecord, WorkItemStore } from '../store/types.js';

/** 重算之后的实例；fixedTitle 是发起时显式给的标题，为 null 时按模板生成 */
export async function refreshDisplay(
  instance: InstanceRecord,
  definition: ProcessDefinition,
  fixedTitle: string | null,
  read: (name: string) => unknown,
  workItems: WorkItemStore,
): Promise<InstanceRecord> {
  const title = fixedTitle ?? titleOf(definition, read);
  const summary: Record<string, unknown> = {};
  for (const name of definition.summary ?? []) summary[name] = read(name);
  return { ...instance, title, summary, current: await currentNodes(instance, definition, workItems) };
}

/** 按模板生成标题；不写模板时用流程名 */
function titleOf(definition: ProcessDefinition, read: (name: string) => unknown): string {
  const template = definition.title;
  if (!template?.trim()) return definition.name;
  return template.replace(/\{([^{}]+)\}/g, (_, name: string) => {
    const value = read(name.trim());
    return value == null ? '' : toText(value);
  });
}

/** 还开着的待办所在的节点，按到达的先后、去重；再加上停着等的节点 */
async function currentNodes(instance: InstanceRecord, definition: ProcessDefinition, workItems: WorkItemStore): Promise<CurrentNode[]> {
  // 结束了的没有正在处理的节点；暂停的照样显示停在哪
  if (instance.endedAt != null) return [];
  const open = new Map<string, CurrentNode>();
  for (const item of await workItems.ofInstance(instance.id)) {
    if (item.state === 'open' && !open.has(item.node)) open.set(item.node, { node: item.node, name: item.nodeName });
  }
  for (const node of Object.keys(instance.state.waits)) {
    if (!open.has(node)) open.set(node, { node, name: definition.nodes.find((n) => n.id === node)?.name ?? null });
  }
  return [...open.values()];
}
