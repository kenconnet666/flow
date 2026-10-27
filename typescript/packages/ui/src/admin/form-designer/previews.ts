/**
 * 按流程节点的权限预览表单：用了这张表单的流程（最新发布的版本）里，开始节点与人工节点上写的字段权限（0005 的节点 form）。
 * 表单设计器的预览里选一个，就按它隐藏、只读、必填，看审批人在那一步看到的样子。
 */
import { parseDefinition, type NodeFormAccess } from '@zerodep-dev/flow-model';
import type { FlowClient } from '../../client/flow-client';

/** 一个能按它预览的节点 */
export interface FormPreview {
  /** 流程标识与节点标识 */
  readonly key: string;
  /** 流程名 · 节点名 */
  readonly label: string;
  readonly access: NodeFormAccess;
  /** 是不是开始节点：没写 default 时开始节点能改、其他只读 */
  readonly start: boolean;
}

/** 用了这张表单的流程里每个开始、人工节点（没写权限的节点按默认：开始能改、其他只读） */
export async function formPreviews(flow: FlowClient, formKey: string, signal?: AbortSignal): Promise<FormPreview[]> {
  const usages = await flow.formUsages(formKey, signal);
  const definitions = await Promise.all(
    usages.map(async (usage) => ({ usage, definition: parseDefinition((await flow.definitionJson(usage.processKey, usage.version, signal)).json) })),
  );
  return definitions.flatMap(({ usage, definition }) =>
    definition.nodes
      .filter((node) => node.type === 'start' || node.type === 'task')
      .map((node) => ({
        key: `${usage.processKey}:${node.id}`,
        label: `${usage.processName} · ${node.name ?? node.id}`,
        access: ('form' in node && node.form) || {},
        start: node.type === 'start',
      })),
  );
}
