/**
 * 流程管理、表单管理共用的列表：全部（停用的也在），按分类、名称排；每行能编辑、导出、复制、停用或启用；
 * 工具栏上"新建""导入"。两者的差别（取哪个列表、怎么启停、怎么导出发布）由 CatalogKind 给。
 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import {
  Alert,
  Badge,
  Button,
  Flex,
  Link,
  pickFile,
  useErrorMessage,
  useTableQuery,
  type DataTableColumn,
} from '@zerodep-dev/vue-tsx-pro-ui';
import { QueryTable } from '@zerodep-dev/vue-tsx-pro-ui/patterns';
import type { FormSummary, ProcessSummary } from '../client/types';
import { useFlowMessages } from '../lang/messages';
import { CopyDialog } from './CopyDialog';
import { downloadJson, type Rename, type Transfer } from './transfer';

/** 列表的一行：流程与表单的摘要字段一样 */
export type CatalogEntry = ProcessSummary | FormSummary;

/** 一类东西（流程或表单）在列表上的差别 */
export interface CatalogKind {
  readonly title: string;
  readonly newLabel: string;
  /** 导出文件名的后缀：leave.process.json */
  readonly fileSuffix: string;
  list(signal?: AbortSignal): Promise<readonly CatalogEntry[]>;
  setActive(key: string, active: boolean): Promise<unknown>;
  readonly transfer: Transfer;
}

/** CatalogList 的属性 */
export interface CatalogListProps {
  kind: CatalogKind;
  /** 编辑一个；新建时 key 为 null */
  onEdit: (key: string | null) => void;
}

/** 流程、表单的列表 */
export const CatalogList = _component(({ kind, onEdit }: CatalogListProps) => {
  const messages = useFlowMessages();
  const errorText = useErrorMessage();
  let busy = _state<string | null>(null);
  let failure = _state<string | null>(null);
  let notice = _state<string | null>(null);
  let copying = _state<CatalogEntry | null>(null);
  let copyOpen = _state(false);
  // 流程、表单都不多，一次取全部，在前端分页
  const query = useTableQuery(
    async (q, signal) => {
      const all = await kind.list(signal);
      return { rows: all.slice((q.page - 1) * q.pageSize, q.page * q.pageSize), total: all.length };
    },
    { pageSize: 20, filters: {} },
  );

  /** 跑一个操作：期间按钮不可用，失败时提示，成功后刷新列表 */
  async function run(key: string, action: () => Promise<string | void>): Promise<boolean> {
    busy = key;
    failure = null;
    notice = null;
    try {
      notice = (await action()) ?? null;
      await query.refresh();
      return true;
    } catch (error) {
      failure = errorText(error);
      return false;
    } finally {
      busy = null;
    }
  }

  const toggle = (entry: CatalogEntry) => run(entry.key, () => kind.setActive(entry.key, entry.status !== 'active').then(() => undefined));
  const exportOne = (entry: CatalogEntry) =>
    run(entry.key, async () => downloadJson(`${entry.key}${kind.fileSuffix}`, await kind.transfer.exportJson(entry.key)));

  async function importFile() {
    const file = await pickFile('.json');
    if (!file) return;
    await run('import', async () => {
      await kind.transfer.publish(await file.text(), undefined, messages.importNote(file.name));
      return messages.imported;
    });
  }

  const copy = (rename: Rename) =>
    run('copy', async () => {
      await kind.transfer.publish(await kind.transfer.exportJson(copying!.key), rename, messages.copyNote(copying!.key));
      return messages.copied(rename.key);
    });

  const columns = (): DataTableColumn<CatalogEntry>[] => [
    {
      key: 'name',
      header: messages.columnTitle,
      value: (e) => e.name,
      cell: (e) => (
        <Link href="#" onClick={(event: MouseEvent) => (event.preventDefault(), onEdit(e.key))}>
          {e.name}
        </Link>
      ),
    },
    { key: 'key', header: messages.columnKey, width: 160, value: (e) => e.key },
    { key: 'category', header: messages.columnCategory, width: 100, value: (e) => e.category ?? '' },
    {
      key: 'status',
      header: messages.columnStatus,
      width: 80,
      value: (e) => e.status,
      cell: (e) => <Badge tone={e.status === 'active' ? 'success' : 'neutral'}>{messages.processStatus[e.status] ?? e.status}</Badge>,
    },
    { key: 'version', header: messages.columnVersion, width: 84, type: 'number', value: (e) => e.latestVersion },
    {
      key: 'actions',
      header: '',
      width: 260,
      value: () => '',
      cell: (e) => (
        <Flex gap="xs">
          <Button size="sm" variant="ghost" onClick={() => onEdit(e.key)}>
            {messages.edit}
          </Button>
          <Button size="sm" variant="ghost" disabled={busy != null} onClick={() => exportOne(e)}>
            {messages.exportJson}
          </Button>
          <Button size="sm" variant="ghost" disabled={busy != null} onClick={() => ((copying = e), (copyOpen = true))}>
            {messages.copy}
          </Button>
          <Button size="sm" variant="ghost" loading={busy === e.key} disabled={busy != null} onClick={() => toggle(e)}>
            {e.status === 'active' ? messages.disable : messages.enable}
          </Button>
        </Flex>
      ),
    },
  ];

  return () => (
    <Flex direction="column" gap="md">
      {failure && <Alert tone="danger" onClose={() => (failure = null)}>{failure}</Alert>}
      {notice && <Alert tone="success" onClose={() => (notice = null)}>{notice}</Alert>}
      <QueryTable
        query={query}
        columns={columns()}
        getKey={(e) => e.key}
        title={kind.title}
        toolbar={
          <Flex gap="sm">
            <Button variant="outline" loading={busy === 'import'} disabled={busy != null} onClick={importFile}>
              {messages.importJson}
            </Button>
            <Button onClick={() => onEdit(null)}>{kind.newLabel}</Button>
          </Flex>
        }
      />
      <CopyDialog _open={copyOpen} source={copying} onConfirm={copy} />
    </Flex>
  );
});
