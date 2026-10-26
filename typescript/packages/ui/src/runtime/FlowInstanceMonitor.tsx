/**
 * 流程监控（管理端）：全部实例，按状态、流程、标题筛，按发起时间从新到旧，服务端分页。点标题打开（多半是带管理员操作的流程详情）。
 * 谁能看由后端的 PermissionPolicy 定（{@code instances.search}）。
 *
 *   <FlowInstanceMonitor onOpen={(instance) => router.push(`/instances/${instance.id}?admin=1`)} />
 */
import type { ProcessInstance } from '@zerodep-dev/flow-model';
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import {
  Badge,
  Field,
  Link,
  Select,
  TextField,
  useRequest,
  useTableQuery,
  type DataTableColumn,
} from '@zerodep-dev/vue-tsx-pro-ui';
import { QueryTable } from '@zerodep-dev/vue-tsx-pro-ui/patterns';
import { useFlowClient } from '../client/flow-client';
import { useFlowMessages } from '../lang/messages';
import { statusTone } from './status';

/** FlowInstanceMonitor 的属性 */
export interface FlowInstanceMonitorProps {
  /** 点了某一行的标题 */
  onOpen: (instance: ProcessInstance) => void;
  /** 每页条数，默认 20 */
  pageSize?: number;
}

/** 查询栏的条件 */
interface MonitorFilters {
  status: string;
  processKey: string;
  keyword: string;
}

const BLANK: MonitorFilters = { status: '', processKey: '', keyword: '' };

/** 流程监控 */
export const FlowInstanceMonitor = _component(({ onOpen, pageSize = 20 }: FlowInstanceMonitorProps) => {
  const flow = useFlowClient();
  const messages = useFlowMessages();
  const processes = useRequest((_: undefined, signal) => flow.processes(signal));
  const query = useTableQuery((q, signal) => flow.instances({ page: q.page, pageSize: q.pageSize, ...q.filters }, signal), {
    pageSize,
    filters: { ...BLANK },
  });

  const statusItems = () => Object.entries(messages.status).map(([value, label]) => ({ value, label }));
  const processItems = () => (processes.data ?? []).map((p) => ({ value: p.key, label: p.name }));
  const processName = (key: string) => processes.data?.find((p) => p.key === key)?.name ?? key;

  const columns = (): DataTableColumn<ProcessInstance>[] => [
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
    { key: 'process', header: messages.columnProcess, width: 120, value: (instance) => processName(instance.processKey) },
    {
      key: 'status',
      header: messages.columnStatus,
      width: 100,
      value: (instance) => instance.status,
      cell: (instance) => <Badge tone={statusTone(instance.status)}>{messages.status[instance.status] ?? instance.status}</Badge>,
    },
    { key: 'current', header: messages.columnCurrent, width: 160, value: (instance) => instance.current.map((c) => c.name ?? c.node).join('、') },
    { key: 'startedBy', header: messages.startedBy, width: 100, value: (instance) => instance.startedByName ?? '' },
    { key: 'startedAt', header: messages.startedAt, width: 170, type: 'dateTime', value: (instance) => new Date(instance.startedAt) },
  ];

  return () => (
    <QueryTable
      query={query}
      columns={columns()}
      getKey={(instance) => String(instance.id)}
      title={messages.monitor}
      blankFilters={BLANK}
      filters={(draft: MonitorFilters) => [
        <Field key="keyword" label={messages.keyword}>
          <TextField _value={draft.keyword} />
        </Field>,
        <Field key="status" label={messages.columnStatus}>
          <Select items={statusItems()} _value={draft.status} placeholder={messages.allStatuses} clearable />
        </Field>,
        <Field key="process" label={messages.columnProcess}>
          <Select items={processItems()} _value={draft.processKey} placeholder={messages.allProcesses} clearable />
        </Field>,
      ]}
    />
  );
});
