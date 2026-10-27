/**
 * 待办列表：我的待办（落到我的，加上我能认领的，按到达时间从新到旧）或我已办（按处理时间从新到旧），
 * 服务端分页（组件库的 QueryTable + useTableQuery）。
 *
 *   <FlowInbox onOpen={(item) => router.push(`/work-items/${item.id}`)} />
 *   <FlowInbox list="done" onOpen={(item) => router.push(`/instances/${item.instanceId}`)} />
 *
 * 标题列是打开的入口；我的待办里等认领的显示"待认领"、过了截止时间的标"已超时"，我已办里是处理结果与流程现在的状态。
 * 业务摘要列（实例上的 summary）由使用方经 columns 加。
 */
import type { TodoItem } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Badge, Button, Flex, Link, Small, useFormat, useTableQuery, type DataTableColumn } from '@zerodep-dev/vue-tsx-pro-ui';
import { QueryTable } from '@zerodep-dev/vue-tsx-pro-ui/patterns';
import { useFlowClient } from '../client/flow-client';
import { useFlowMessages } from '../lang/messages';
import { BLANK_LIST_FILTERS, listFilterParams, useListFilters } from './list-filters';
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
  // 我已办能按流程、处理时间筛；待办、待我指定不筛
  const filterFields = useListFilters();
  const query = useTableQuery(
    (q, signal) =>
      list === 'done'
        ? flow.done({ page: q.page, pageSize: q.pageSize, ...listFilterParams(q.filters) }, signal)
        : flow[list]({ page: q.page, pageSize: q.pageSize }, signal),
    { pageSize, filters: { ...BLANK_LIST_FILTERS } },
  );

  const titleColumn = (): DataTableColumn<TodoItem> => ({
    key: 'title',
    header: messages.columnTitle,
    value: (item) => item.instance?.title ?? item.processKey,
    cell: (item) => (
      <Link href="#" onClick={(event: MouseEvent) => (event.preventDefault(), onOpen(item))}>
        {item.instance?.title ?? item.processKey}
      </Link>
    ),
  });
  const nodeColumn = (): DataTableColumn<TodoItem> => ({
    key: 'node',
    header: messages.columnNode,
    width: 120,
    value: (item) => item.nodeName ?? item.node,
  });

  const todoColumns = (): DataTableColumn<TodoItem>[] => [
    titleColumn(),
    nodeColumn(),
    {
      key: 'assignee',
      header: messages.columnAssignee,
      width: 100,
      value: (item) => item.assigneeName ?? '',
      cell: (item) =>
        item.assignment === 'pool' ? <Badge tone="warning">{messages.pool}</Badge> : item.assigneeName,
    },
    {
      key: 'createdAt',
      header: messages.columnArrivedAt,
      width: 170,
      type: 'dateTime',
      value: (item) => item.createdAt,
    },
    {
      key: 'dueAt',
      header: messages.columnDue,
      width: 190,
      value: (item) => item.dueAt,
      // 过了截止时间的标红：时刻按浏览器的时区显示
      cell: (item) =>
        item.dueAt && (
          <Flex gap="xs" align="center" wrap>
            {format.date(item.dueAt, { dateStyle: 'medium', timeStyle: 'short' })}
            {item.dueAt.toDate().getTime() < Date.now() && <Badge tone="danger">{messages.overdue}</Badge>}
          </Flex>
        ),
    },
  ];

  const doneColumns = (): DataTableColumn<TodoItem>[] => [
    titleColumn(),
    nodeColumn(),
    // 隐式的结果（加签、委派办完）没有名字，显示键
    { key: 'outcome', header: messages.columnOutcome, width: 120, value: (item) => item.outcomeLabel ?? item.outcome ?? '' },
    {
      key: 'status',
      header: messages.columnStatus,
      width: 100,
      value: (item) => item.instance?.status ?? '',
      cell: (item) => item.instance && <Badge tone={statusTone(item.instance.status)}>{messages.status[item.instance.status] ?? item.instance.status}</Badge>,
    },
    {
      key: 'completedAt',
      header: messages.columnCompletedAt,
      width: 180,
      type: 'dateTime',
      value: (item) => item.completedAt,
    },
  ];

  // 我的待办能多选：批量认领、批量同意（各自成败，没做成的列出来）
  let selected = _state<string[]>([]);
  let busy = _state<string | null>(null);
  let report = _state<string | null>(null);

  async function batch(kind: 'claim' | 'approve') {
    const ids = selected.map((key) => BigInt(key));
    busy = kind;
    report = null;
    try {
      const { results } = kind === 'claim' ? await flow.batchClaim(ids) : await flow.batchComplete(ids);
      const failed = results.filter((r) => !r.ok);
      report = messages.batchReport(results.length - failed.length, failed.length);
      selected = [];
      await query.refresh();
    } finally {
      busy = null;
    }
  }

  const toolbar = () =>
    list === 'todo' && (
      <Flex gap="sm" align="center">
        <Button size="sm" variant="outline" disabled={selected.length === 0 || busy != null} loading={busy === 'claim'} onClick={() => void batch('claim')}>
          {messages.batchClaim}
        </Button>
        <Button size="sm" variant="outline" disabled={selected.length === 0 || busy != null} loading={busy === 'approve'} onClick={() => void batch('approve')}>
          {messages.batchApprove}
        </Button>
        {report && <Small tone="muted">{report}</Small>}
      </Flex>
    );

  return () => (
    <QueryTable
      query={query}
      columns={[...(list === 'done' ? doneColumns() : todoColumns()), ...columns]}
      getKey={(item) => String(item.id)}
      {...(list === 'done' ? { blankFilters: BLANK_LIST_FILTERS, filters: filterFields } : {})}
      {...(list === 'todo' ? { selectionMode: 'multiple' as const, _selected: selected, 'onUpdate:_selected': (keys: string[]) => (selected = keys), toolbar: toolbar() } : {})}
      title={title ?? (list === 'done' ? messages.doneList : list === 'designations' ? messages.designationList : messages.inbox)}
    />
  );
});
