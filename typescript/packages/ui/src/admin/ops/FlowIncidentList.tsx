/**
 * 异常处理（管理端）：重试用完的作业（多半是自动节点调外部系统失败），新的在前，服务端分页。每行能重试、跳过并按结果往下走；
 * 点标题打开流程详情（终止、跳转在那里）。谁能看、能做由后端的 PermissionPolicy 定（{@code incidents}、{@code retryJob}、{@code skipJob}）。
 *
 *   <FlowIncidentList onOpen={(incident) => router.push(`/instances/${incident.instanceId}?admin=1`)} />
 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Flex, Link, Text, useFormat, useTableQuery, type DataTableColumn } from '@zerodep-dev/vue-tsx-pro-ui';
import { QueryTable } from '@zerodep-dev/vue-tsx-pro-ui/patterns';
import { useFlowClient } from '../client/flow-client';
import type { Incident } from '../client/types';
import { useFlowMessages } from '../lang/messages';
import { IncidentActions, incidentDoneText, isDelivery, type IncidentAction } from '../runtime/IncidentActions';

/** FlowIncidentList 的属性 */
export interface FlowIncidentListProps {
  /** 点了某一行的标题 */
  onOpen: (incident: Incident) => void;
  /** 每页条数，默认 20 */
  pageSize?: number;
}

/** 异常处理 */
export const FlowIncidentList = _component(({ onOpen, pageSize = 20 }: FlowIncidentListProps) => {
  const flow = useFlowClient();
  const messages = useFlowMessages();
  const format = useFormat();
  let notice = _state<string | null>(null);
  let failure = _state<string | null>(null);
  const query = useTableQuery((q, signal) => flow.incidents({ page: q.page, pageSize: q.pageSize }, signal), { pageSize, filters: {} });

  function done(kind: IncidentAction) {
    failure = null;
    notice = incidentDoneText(kind, messages);
    void query.refresh();
  }

  const columns = (): DataTableColumn<Incident>[] => [
    {
      key: 'title',
      header: messages.columnTitle,
      value: (row) => row.instanceTitle ?? String(row.instanceId),
      cell: (row) => (
        <Link href="#" onClick={(event: MouseEvent) => (event.preventDefault(), onOpen(row))}>
          {row.instanceTitle ?? String(row.instanceId)}
        </Link>
      ),
    },
    { key: 'node', header: messages.columnNode, width: 120, value: (row) => row.nodeName ?? row.node ?? (isDelivery(row) ? messages.incidentDelivery : '') },
    {
      key: 'error',
      header: messages.columnError,
      value: (row) => row.error ?? '',
      cell: (row) => <Text tone="danger">{row.error ?? ''}</Text>,
    },
    { key: 'attempts', header: messages.columnAttempts, width: 84, type: 'number', value: (row) => row.attempts },
    {
      key: 'failedAt',
      header: messages.columnFailedAt,
      width: 150,
      value: (row) => row.failedAt,
      cell: (row) => format.date(row.failedAt, { dateStyle: 'short', timeStyle: 'short' }),
    },
    {
      key: 'actions',
      header: '',
      width: 120,
      value: () => '',
      cell: (row) => <IncidentActions incident={row} onDone={done} onError={(message) => ((failure = message), (notice = null))} />,
    },
  ];

  return () => (
    <Flex direction="column" gap="md">
      {failure && <Alert tone="danger" onClose={() => (failure = null)}>{failure}</Alert>}
      {notice && <Alert tone="success" onClose={() => (notice = null)}>{notice}</Alert>}
      <QueryTable query={query} columns={columns()} getKey={(row) => String(row.jobId)} title={messages.incidents} />
    </Flex>
  );
});
