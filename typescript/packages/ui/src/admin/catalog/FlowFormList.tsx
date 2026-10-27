/**
 * 表单管理（管理端）：全部表单（停用的也在），每行能编辑、导出、复制、停用或启用；工具栏上"新建表单""导入"。
 * 停用的表单新发布的流程不能再选它，已经引用它的流程照常用。谁能做由后端的 PermissionPolicy 定（{@code forms.*}）。
 *
 *   <FlowFormList onEdit={(key) => router.push(key ? `/admin/forms/${key}` : '/admin/forms/new')} />
 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { useFlowClient } from '../../client/flow-client';
import { useFlowMessages } from '../../lang/messages';
import { CatalogList, type CatalogKind } from './CatalogList';
import { formTransfer } from './transfer';

/** FlowFormList 的属性 */
export interface FlowFormListProps {
  /** 编辑一张表单；新建时 key 为 null */
  onEdit: (key: string | null) => void;
}

/** 表单管理 */
export const FlowFormList = _component(({ onEdit }: FlowFormListProps) => {
  const flow = useFlowClient();
  const messages = useFlowMessages();
  const kind: CatalogKind = {
    title: messages.formManage,
    newLabel: messages.newForm,
    fileSuffix: '.form.json',
    list: (signal) => flow.forms(signal),
    setActive: (key, active) => flow.setFormActive(key, active),
    transfer: formTransfer(flow),
  };
  return () => <CatalogList kind={kind} onEdit={onEdit} />;
});
