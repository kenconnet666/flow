/**
 * 表单管理（管理端）：全部表单（停用的也在），按分类、名称排；每行能编辑、停用或启用；工具栏上"新建表单"。
 * 停用的表单新发布的流程不能再选它，已经引用它的流程照常用。谁能做由后端的 PermissionPolicy 定（{@code forms.*}）。
 *
 *   <FlowFormList onEdit={(key) => router.push(key ? `/admin/forms/${key}` : '/admin/forms/new')} />
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
import type { FormSummary } from '../client/types';
import { useFlowMessages } from '../lang/messages';

/** FlowFormList 的属性 */
export interface FlowFormListProps {
  /** 编辑一张表单；新建时 key 为 null */
  onEdit: (key: string | null) => void;
}

/** 表单管理 */
export const FlowFormList = _component(({ onEdit }: FlowFormListProps) => {
  const flow = useFlowClient();
  const messages = useFlowMessages();
  const errorText = useErrorMessage();
  let busy = _state<string | null>(null);
  let failure = _state<string | null>(null);
  // 表单不多，一次取全部，在前端分页
  const query = useTableQuery(
    async (q, signal) => {
      const all = await flow.forms(signal);
      return { rows: all.slice((q.page - 1) * q.pageSize, q.page * q.pageSize), total: all.length };
    },
    { pageSize: 20, filters: {} },
  );

  async function toggle(form: FormSummary) {
    busy = form.key;
    failure = null;
    try {
      await flow.setFormActive(form.key, form.status !== 'active');
      await query.refresh();
    } catch (error) {
      failure = errorText(error);
    } finally {
      busy = null;
    }
  }

  const columns = (): DataTableColumn<FormSummary>[] => [
    {
      key: 'name',
      header: messages.columnTitle,
      value: (f) => f.name,
      cell: (f) => (
        <Link href="#" onClick={(event: MouseEvent) => (event.preventDefault(), onEdit(f.key))}>
          {f.name}
        </Link>
      ),
    },
    { key: 'key', header: messages.columnKey, width: 160, value: (f) => f.key },
    { key: 'category', header: messages.columnCategory, width: 100, value: (f) => f.category ?? '' },
    {
      key: 'status',
      header: messages.columnStatus,
      width: 80,
      value: (f) => f.status,
      cell: (f) => <Badge tone={f.status === 'active' ? 'success' : 'neutral'}>{messages.processStatus[f.status] ?? f.status}</Badge>,
    },
    { key: 'version', header: messages.columnVersion, width: 84, type: 'number', value: (f) => f.latestVersion },
    {
      key: 'actions',
      header: '',
      width: 140,
      value: () => '',
      cell: (f) => (
        <Flex gap="xs">
          <Button size="sm" variant="ghost" onClick={() => onEdit(f.key)}>
            {messages.edit}
          </Button>
          <Button size="sm" variant="ghost" loading={busy === f.key} disabled={busy != null} onClick={() => toggle(f)}>
            {f.status === 'active' ? messages.disable : messages.enable}
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
        getKey={(f) => f.key}
        title={messages.formManage}
        toolbar={<Button onClick={() => onEdit(null)}>{messages.newForm}</Button>}
      />
    </Flex>
  );
});
