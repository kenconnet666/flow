/**
 * 收件箱：我的待办（落到我的，加上我能认领的），按到达时间从新到旧，服务端分页（组件库的 QueryTable + useTableQuery）。
 *
 *   <FlowInbox onOpen={(item) => router.push(`/work-items/${item.workItem.id}`)} />
 *
 * 标题列是"办理"的入口；在收件箱里等认领的显示"待认领"。业务摘要列（实例上的 summary）由使用方经 columns 加。
 */
import type { TodoItem } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Badge, Link, useTableQuery, type DataTableColumn } from '@zerodep-dev/vue-tsx-pro-ui';
import { QueryTable } from '@zerodep-dev/vue-tsx-pro-ui/patterns';
import { useFlowClient } from '../client/flow-client';
import { useFlowMessages } from '../lang/messages';

/** FlowInbox 的属性 */
export interface FlowInboxProps {
  /** 点了某一行的标题 */
  onOpen: (item: TodoItem) => void;
  /** 加在默认列之后的列（业务摘要：金额、供应商……） */
  columns?: readonly DataTableColumn<TodoItem>[];
  /** 每页条数，默认 20 */
  pageSize?: number;
  /** 标题；默认"我的待办" */
  title?: string;
}

/** 收件箱 */
export const FlowInbox = _component(({ onOpen, columns = [], pageSize = 20, title }: FlowInboxProps) => {
  const flow = useFlowClient();
  const messages = useFlowMessages();
  const query = useTableQuery((q, signal) => flow.todo({ page: q.page, pageSize: q.pageSize }, signal), {
    pageSize,
    // 收件箱现在没有筛选条件
    filters: {},
  });

  const defaults = (): DataTableColumn<TodoItem>[] => [
    {
      key: 'title',
      header: messages.columnTitle,
      value: (item) => item.title ?? item.workItem.processKey,
      cell: (item) => (
        <Link href="#" onClick={(event: MouseEvent) => (event.preventDefault(), onOpen(item))}>
          {item.title ?? item.workItem.processKey}
        </Link>
      ),
    },
    { key: 'node', header: messages.columnNode, width: 140, value: (item) => item.workItem.nodeName ?? item.workItem.node },
    {
      key: 'assignee',
      header: messages.columnAssignee,
      width: 120,
      value: (item) => item.workItem.assigneeName ?? '',
      cell: (item) =>
        item.workItem.assignment === 'pool' ? <Badge tone="warning">{messages.pool}</Badge> : item.workItem.assigneeName,
    },
    {
      key: 'createdAt',
      header: messages.columnArrivedAt,
      width: 180,
      type: 'dateTime',
      value: (item) => new Date(item.workItem.createdAt),
    },
  ];

  return () => (
    <QueryTable
      query={query}
      columns={[...defaults(), ...columns]}
      getKey={(item) => String(item.workItem.id)}
      title={title ?? messages.inbox}
    />
  );
});
