/**
 * 待办列表：我的待办（落到我的，加上我能认领的，按到达时间从新到旧）或我已办（按处理时间从新到旧），
 * 服务端分页（组件库的 QueryTable + useTableQuery）。
 *
 *   <FlowInbox onOpen={(item) => router.push(`/work-items/${item.workItem.id}`)} />
 *   <FlowInbox list="done" onOpen={(item) => router.push(`/instances/${item.workItem.instanceId}`)} />
 *
 * 标题列是打开的入口；我的待办里等认领的显示"待认领"、过了截止时间的标"已超时"，我已办里是处理结果与流程现在的状态。
 * 业务摘要列（实例上的 summary）由使用方经 columns 加。
 */
import type { TodoItem } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Badge, Flex, Link, useFormat, useTableQuery, type DataTableColumn } from '@zerodep-dev/vue-tsx-pro-ui';
import { QueryTable } from '@zerodep-dev/vue-tsx-pro-ui/patterns';
import { useFlowClient } from '../client/flow-client';
import { useFlowMessages } from '../lang/messages';
import { statusTone } from './status';

/** FlowInbox 的属性 */
export interface FlowInboxProps {
  /** todo 我的待办（默认），done 我已办，designations 等我指定承办人的 */
  list?: 'todo' | 'done' | 'designations';
  /** 点了某一行的标题 */
  onOpen: (item: TodoItem) => void;
  /** 加在默认列之后的列（业务摘要：金额、供应商……） */
  columns?: readonly DataTableColumn<TodoItem>[];
  /** 每页条数，默认 20 */
  pageSize?: number;
  /** 标题；默认"我的待办"或"我已办" */
  title?: string;
}

/** 待办列表 */
export const FlowInbox = _component(({ list = 'todo', onOpen, columns = [], pageSize = 20, title }: FlowInboxProps) => {
  const flow = useFlowClient();
  const messages = useFlowMessages();
  const format = useFormat();
  const query = useTableQuery((q, signal) => flow[list]({ page: q.page, pageSize: q.pageSize }, signal), {
    pageSize,
    // 两个列表现在都没有筛选条件
    filters: {},
  });

  const titleColumn = (): DataTableColumn<TodoItem> => ({
    key: 'title',
    header: messages.columnTitle,
    value: (item) => item.title ?? item.workItem.processKey,
    cell: (item) => (
      <Link href="#" onClick={(event: MouseEvent) => (event.preventDefault(), onOpen(item))}>
        {item.title ?? item.workItem.processKey}
      </Link>
    ),
  });
  const nodeColumn = (): DataTableColumn<TodoItem> => ({
    key: 'node',
    header: messages.columnNode,
    width: 120,
    value: (item) => item.workItem.nodeName ?? item.workItem.node,
  });

  const todoColumns = (): DataTableColumn<TodoItem>[] => [
    titleColumn(),
    nodeColumn(),
    {
      key: 'assignee',
      header: messages.columnAssignee,
      width: 100,
      value: (item) => item.workItem.assigneeName ?? '',
      cell: (item) =>
        item.workItem.assignment === 'pool' ? <Badge tone="warning">{messages.pool}</Badge> : item.workItem.assigneeName,
    },
    {
      key: 'createdAt',
      header: messages.columnArrivedAt,
      width: 170,
      type: 'dateTime',
      value: (item) => new Date(item.workItem.createdAt),
    },
    {
      key: 'dueAt',
      header: messages.columnDue,
      width: 190,
      value: (item) => (item.workItem.dueAt ? new Date(item.workItem.dueAt) : null),
      // 过了截止时间的标红：时刻按浏览器的时区显示
      cell: (item) =>
        item.workItem.dueAt && (
          <Flex gap="xs" align="center" wrap>
            {format.date(item.workItem.dueAt, { dateStyle: 'medium', timeStyle: 'short' })}
            {Date.parse(item.workItem.dueAt) < Date.now() && <Badge tone="danger">{messages.overdue}</Badge>}
          </Flex>
        ),
    },
  ];

  const doneColumns = (): DataTableColumn<TodoItem>[] => [
    titleColumn(),
    nodeColumn(),
    // 隐式的结果（加签、委派办完）没有名字，显示键
    { key: 'outcome', header: messages.columnOutcome, width: 120, value: (item) => item.outcomeLabel ?? item.workItem.outcome ?? '' },
    {
      key: 'status',
      header: messages.columnStatus,
      width: 100,
      value: (item) => item.status ?? '',
      cell: (item) => item.status && <Badge tone={statusTone(item.status)}>{messages.status[item.status] ?? item.status}</Badge>,
    },
    {
      key: 'completedAt',
      header: messages.columnCompletedAt,
      width: 180,
      type: 'dateTime',
      value: (item) => (item.workItem.completedAt ? new Date(item.workItem.completedAt) : null),
    },
  ];

  return () => (
    <QueryTable
      query={query}
      columns={[...(list === 'done' ? doneColumns() : todoColumns()), ...columns]}
      getKey={(item) => String(item.workItem.id)}
      title={title ?? (list === 'done' ? messages.doneList : list === 'designations' ? messages.designationList : messages.inbox)}
    />
  );
});
