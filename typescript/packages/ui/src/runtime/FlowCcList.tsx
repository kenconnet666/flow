/**
 * 抄送我的：未读在前，服务端分页。点标题时先标成已读，再交给使用方打开（多半是流程详情）。
 *
 *   <FlowCcList onOpen={(item) => router.push(`/instances/${item.instanceId}`)} />
 *
 * 抄送人一列：手动抄送的是抄送人，抄送节点的是节点名。业务摘要列由使用方经 columns 加。
 */
import type { CcItem } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Badge, Flex, Link, useTableQuery, type DataTableColumn } from '@zerodep-dev/vue-tsx-pro-ui';
import { QueryTable } from '@zerodep-dev/vue-tsx-pro-ui/patterns';
import { useFlowClient } from '../client/flow-client';
import { useFlowMessages } from '../lang/messages';
import { BLANK_LIST_FILTERS, listFilterParams, useListFilters } from './list-filters';
import { statusTone } from './status';

/** FlowCcList 的属性 */
export interface FlowCcListProps {
  /** 点了某一行的标题（已经标成已读） */
  onOpen: (item: CcItem) => void;
  /** 加在默认列之后的列 */
  columns?: readonly DataTableColumn<CcItem>[];
  /** 每页条数，默认 20 */
  pageSize?: number;
  /** 标题；默认"抄送我的" */
  title?: string;
}

/** 抄送我的 */
export const FlowCcList = _component(({ onOpen, columns = [], pageSize = 20, title }: FlowCcListProps) => {
  const flow = useFlowClient();
  const messages = useFlowMessages();
  const filterFields = useListFilters();
  const query = useTableQuery((q, signal) => flow.ccList({ page: q.page, pageSize: q.pageSize, sorts: q.sorts, ...listFilterParams(q.filters) }, signal), {
    pageSize,
    filters: { ...BLANK_LIST_FILTERS },
  });

  async function open(item: CcItem) {
    // 标已读失败（网络抖动）不拦着打开：下次进列表还是未读
    if (!item.readAt) await flow.readCc(item.instanceId).catch(() => undefined);
    onOpen(item);
  }

  const defaults = (): DataTableColumn<CcItem>[] => [
    {
      key: 'title',
      header: messages.columnTitle,
      value: (item) => item.instance?.title ?? '',
      cell: (item) => (
        <Flex gap="xs" align="center">
          <Link href="#" onClick={(event: MouseEvent) => (event.preventDefault(), void open(item))}>
            {item.instance?.title ?? String(item.instanceId)}
          </Link>
          {!item.readAt && <Badge tone="accent">{messages.unread}</Badge>}
        </Flex>
      ),
    },
    {
      key: 'from',
      header: messages.columnCcFrom,
      width: 120,
      value: (item) => item.fromUserName ?? item.nodeName ?? messages.system,
    },
    { key: 'message', header: messages.columnMessage, value: (item) => item.message ?? '' },
    {
      key: 'status',
      header: messages.columnStatus,
      width: 100,
      value: (item) => item.instance?.status ?? '',
      cell: (item) => item.instance && <Badge tone={statusTone(item.instance.status)}>{messages.status[item.instance.status] ?? item.instance.status}</Badge>,
    },
    { key: 'createdAt', header: messages.columnCcAt, width: 170, type: 'dateTime', sortable: true, value: (item) => item.createdAt },
  ];

  return () => (
    <QueryTable
      query={query}
      columns={[...defaults(), ...columns]}
      getKey={(item) => String(item.id)}
      blankFilters={BLANK_LIST_FILTERS}
      filters={filterFields}
      title={title ?? messages.ccList}
    />
  );
});
