/**
 * 流程管理（管理端）：全部流程（停用的也在），每行能编辑定义、导出、复制、停用或启用；工具栏上"新建流程""导入"。
 * 谁能做由后端的 PermissionPolicy 定（{@code processes.list}、{@code definitions.*}）。
 *
 *   <FlowProcessList onEdit={(key) => router.push(key ? `/admin/processes/${key}` : '/admin/processes/new')} />
 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { useFlowClient } from '../client/flow-client';
import { useFlowMessages } from '../lang/messages';
import { CatalogList, type CatalogKind } from './CatalogList';
import { processTransfer } from './transfer';

/** FlowProcessList 的属性 */
export interface FlowProcessListProps {
  /** 编辑一个流程的定义；新建时 key 为 null */
  onEdit: (key: string | null) => void;
}

/** 流程管理 */
export const FlowProcessList = _component(({ onEdit }: FlowProcessListProps) => {
  const flow = useFlowClient();
  const messages = useFlowMessages();
  const kind: CatalogKind = {
    title: messages.processManage,
    newLabel: messages.newProcess,
    fileSuffix: '.process.json',
    list: (signal) => flow.processes(signal),
    setActive: (key, active) => flow.setProcessActive(key, active),
    transfer: processTransfer(flow),
  };
  return () => <CatalogList kind={kind} onEdit={onEdit} />;
});
