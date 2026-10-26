/**
 * 我发起的：按发起时间从新到旧，看每个申请走到哪了（状态、当前环节），服务端分页。
 *
 *   <FlowStartedList onOpen={(instance) => router.push(`/instances/${instance.id}`)} />
 *
 * 业务摘要列（实例上的 summary）由使用方经 columns 加。
 */
import type { ProcessInstance } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Badge, Link, useTableQuery, type DataTableColumn } from '@zerodep-dev/vue-tsx-pro-ui';
import { QueryTable } from '@zerodep-dev/vue-tsx-pro-ui/patterns';
import { useFlowClient } from '../client/flow-client';
import { useFlowMessages } from '../lang/messages';
import { statusTone } from './status';

/** FlowStartedList 的属性 */
export interface FlowStartedListProps {
  /** 点了某一行的标题 */
  onOpen: (instance: ProcessInstance) => void;
  /** 加在默认列之后的列 */
  columns?: readonly DataTableColumn<ProcessInstance>[];
  /** 每页条数，默认 20 */
  pageSize?: number;
  /** 标题；默认"我发起的" */
  title?: string;
}

/** 我发起的 */
export const FlowStartedList = _component(({ onOpen, columns = [], pageSize = 20, title }: FlowStartedListProps) => {
  const flow = useFlowClient();
  const messages = useFlowMessages();
  const query = useTableQuery((q, signal) => flow.started({ page: q.page, pageSize: q.pageSize }, signal), {
    pageSize,
    filters: {},
  });

  const defaults = (): DataTableColumn<ProcessInstance>[] => [
    {
      key: 'title',
      header: messages.columnTitle,
      value: (instance) => instance.title ?? instance.processKey,
      cell: (instance) => (
        <Link href="#" onClick={(event: MouseEvent) => (event.preventDefault(), onOpen(instance))}>
          {instance.title ?? instance.processKey}
        </Link>
      ),
    },
    {
      key: 'status',
      header: messages.columnStatus,
      width: 100,
      value: (instance) => instance.status,
      cell: (instance) => <Badge tone={statusTone(instance.status)}>{messages.status[instance.status] ?? instance.status}</Badge>,
    },
    {
      key: 'current',
      header: messages.columnCurrent,
      width: 180,
      value: (instance) => instance.current.map((c) => c.name ?? c.node).join('、'),
    },
    {
      key: 'startedAt',
      header: messages.startedAt,
      width: 180,
      type: 'dateTime',
      value: (instance) => new Date(instance.startedAt),
    },
  ];

  return () => (
    <QueryTable
      query={query}
      columns={[...defaults(), ...columns]}
      getKey={(instance) => String(instance.id)}
      title={title ?? messages.startedList}
    />
  );
});
