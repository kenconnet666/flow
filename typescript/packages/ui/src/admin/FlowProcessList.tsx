/**
 * 流程管理（管理端）：全部流程（停用的也在），按分类、名称排；每行能编辑定义、停用或启用；工具栏上"新建流程"。
 * 谁能做由后端的 PermissionPolicy 定（{@code processes.list}、{@code definitions.*}）。
 *
 *   <FlowProcessList onEdit={(key) => router.push(key ? `/admin/processes/${key}` : '/admin/processes/new')} />
 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import {
  Alert,
  Badge,
  Button,
  Flex,
  Link,
  useErrorMessage,
  useTableQuery,
  type DataTableColumn,
} from '@zerodep-dev/vue-tsx-pro-ui';
import { QueryTable } from '@zerodep-dev/vue-tsx-pro-ui/patterns';
import { useFlowClient } from '../client/flow-client';
import type { ProcessSummary } from '../client/types';
import { useFlowMessages } from '../lang/messages';

/** FlowProcessList 的属性 */
export interface FlowProcessListProps {
  /** 编辑一个流程的定义；新建时 key 为 null */
  onEdit: (key: string | null) => void;
}

/** 流程管理 */
export const FlowProcessList = _component(({ onEdit }: FlowProcessListProps) => {
  const flow = useFlowClient();
  const messages = useFlowMessages();
  const errorText = useErrorMessage();
  let busy = _state<string | null>(null);
  let failure = _state<string | null>(null);
  // 流程不多，一次取全部，在前端分页
  const query = useTableQuery(
    async (q, signal) => {
      const all = await flow.processes(signal);
      return { rows: all.slice((q.page - 1) * q.pageSize, q.page * q.pageSize), total: all.length };
    },
    { pageSize: 20, filters: {} },
  );

  async function toggle(process: ProcessSummary) {
    busy = process.key;
    failure = null;
    try {
      await flow.setProcessActive(process.key, process.status !== 'active');
      await query.refresh();
    } catch (error) {
      failure = errorText(error);
    } finally {
      busy = null;
    }
  }

  const columns = (): DataTableColumn<ProcessSummary>[] => [
    {
      key: 'name',
      header: messages.columnTitle,
      value: (p) => p.name,
      cell: (p) => (
        <Link href="#" onClick={(event: MouseEvent) => (event.preventDefault(), onEdit(p.key))}>
          {p.name}
        </Link>
      ),
    },
    { key: 'key', header: messages.columnKey, width: 140, value: (p) => p.key },
    { key: 'category', header: messages.columnCategory, width: 100, value: (p) => p.category ?? '' },
    {
      key: 'status',
      header: messages.columnStatus,
      width: 80,
      value: (p) => p.status,
      cell: (p) => <Badge tone={p.status === 'active' ? 'success' : 'neutral'}>{messages.processStatus[p.status] ?? p.status}</Badge>,
    },
    { key: 'version', header: messages.columnVersion, width: 84, type: 'number', value: (p) => p.latestVersion },
    {
      key: 'actions',
      header: '',
      width: 140,
      value: () => '',
      cell: (p) => (
        <Flex gap="xs">
          <Button size="sm" variant="ghost" onClick={() => onEdit(p.key)}>
            {messages.edit}
          </Button>
          <Button size="sm" variant="ghost" loading={busy === p.key} disabled={busy != null} onClick={() => toggle(p)}>
            {p.status === 'active' ? messages.disable : messages.enable}
          </Button>
        </Flex>
      ),
    },
  ];

  return () => (
    <Flex direction="column" gap="md">
      {failure && <Alert tone="danger" onClose={() => (failure = null)}>{failure}</Alert>}
      <QueryTable
        query={query}
        columns={columns()}
        getKey={(p) => p.key}
        title={messages.processManage}
        toolbar={<Button onClick={() => onEdit(null)}>{messages.newProcess}</Button>}
      />
    </Flex>
  );
});
