/**
 * 编辑器顶上的草稿提示（流程编辑器、表单编辑器共用）：打开时接着谁的草稿、自动保存到哪了、和别人冲突了怎么办。
 *
 *   <DraftBanner opened={openedDraft} status={draft.status} onDiscard={discardDraft} onReload={reloadDraft} />
 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Button, Flex, Small, useFormat } from '@zerodep-dev/vue-tsx-pro-ui';
import { useFlowMessages } from '../../lang/messages';
import { conflictWith, type DesignDraftStatus } from './design-draft';

/** DraftBanner 的属性 */
export interface DraftBannerProps {
  /** 打开时接着的草稿：谁、什么时候存的；不是接着草稿打开的为 null */
  opened: { by: string | null; at: Date } | null;
  /** 自动保存的状态 */
  status: DesignDraftStatus;
  /** 放弃草稿 */
  onDiscard: () => void;
  /** 冲突后载入别人存的草稿 */
  onReload: () => void;
}

/** 草稿提示 */
export const DraftBanner = _component(({ opened, status, onDiscard, onReload }: DraftBannerProps) => {
  const messages = useFlowMessages();
  const format = useFormat();
  const time = (at: Date) => format.date(at, { dateStyle: 'medium', timeStyle: 'short' });

  return () => (
    <>
      {status.state === 'conflict' && (
        <Alert tone="warning">
          <Flex gap="sm" align="center" wrap>
            {messages.draftConflict(conflictWith(status))}
            <Button size="sm" variant="outline" onClick={onReload}>{messages.reloadDraft}</Button>
          </Flex>
        </Alert>
      )}
      {opened && status.state !== 'conflict' && (
        <Alert tone="info">
          <Flex gap="sm" align="center" wrap>
            {messages.draftOpened(opened.by, time(opened.at))}
            <Button size="sm" variant="outline" tone="danger" onClick={onDiscard}>{messages.discardDraft}</Button>
          </Flex>
        </Alert>
      )}
      {status.state === 'saving' && <Small tone="muted">{messages.draftSaving}</Small>}
      {status.state === 'saved' && status.saved && <Small tone="muted">{messages.designDraftSaved(time(status.saved.updatedAt.toDate()))}</Small>}
      {status.state === 'failed' && <Small tone="danger">{messages.draftSaveFailed}</Small>}
    </>
  );
});
