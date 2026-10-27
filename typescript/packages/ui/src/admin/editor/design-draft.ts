/**
 * 设计中的草稿（0004 的 5.2）：流程编辑器、表单编辑器共用。改了停一会儿自动保存到后端，带着版本防两个人互相覆盖；
 * 打开时有草稿就接着草稿改，发布后草稿由后端删掉。"停一会儿再存、和上次一样不存、冲突后停下"是组件库的 useAutosave，
 * 这里只管草稿的接口与版本号。
 *
 *   const draft = useDesignDraft({ kind: 'process', key: () => processKey, content: currentJson, … });
 *   const opened = await draft.open();      // 有草稿时返回它（编辑器换成草稿的内容），没有时 null
 *   draft.ready();                         // 编辑器载入好了（或换成了别的内容）：从这一刻起改了就存
 *   draft.published();                     // 发布成功：草稿没了，之后再改重新开一份
 */
import { HttpError, useAutosave, type AutosaveStatus } from '@zerodep-dev/vue-tsx-pro-ui';
import { useFlowClient } from '../../client/flow-client';
import type { DesignDraft, DraftKind } from '../../client/types';

/** useDesignDraft 的选项 */
export interface DesignDraftOptions {
  kind: DraftKind;
  /** 流程、表单的标识；还没有时（新建、没填标识）不保存 */
  key: () => string | null;
  name: () => string;
  /** 现在的内容：流程是定义的 JSON，表单是源码与清单的 JSON */
  content: () => string;
  /** 基于哪个已发布的版本改的；新建的为 null */
  baseVersion: () => number | null;
}

/** 草稿的自动保存状态 */
export type DesignDraftStatus = AutosaveStatus<DesignDraft>;

/** 冲突时先存的那个人（后端的 flow.concurrent-modification 带着 updatedBy）；不是冲突时为 null */
export function conflictWith(status: DesignDraftStatus): string | null {
  return status.state === 'conflict' && status.error instanceof HttpError ? String(status.error.params['updatedBy'] ?? '') || null : null;
}

/** 草稿的自动保存 */
export function useDesignDraft(options: DesignDraftOptions) {
  const flow = useFlowClient();
  /** 草稿行的版本：存的时候带上，别人先存过时后端报冲突 */
  let rev: number | null = null;
  /** rev 是哪个标识下的那一行：新建时改了标识，草稿换到新标识下从头存，旧标识下的那份删掉（不然带着旧行的版本去存新行会误报冲突） */
  let revKey: string | null = null;
  const autosave = useAutosave<DesignDraft>({
    content: options.content,
    enabled: () => options.key() != null,
    isConflict: (error) => error instanceof HttpError && error.code === 'flow.concurrent-modification',
    save: async (content) => {
      const key = options.key()!;
      if (revKey !== null && revKey !== key) {
        await flow.discardDesignDraft(options.kind, revKey);
        rev = null;
      }
      const saved = await flow.saveDesignDraft({
        kind: options.kind,
        key,
        name: options.name(),
        content,
        baseVersion: options.baseVersion(),
        rev,
      });
      rev = saved.rev;
      revKey = key;
      return saved;
    },
  });
  /** 打开时读到的草稿：ready 时交给自动保存当作"已存的" */
  let opened: DesignDraft | null = null;

  /** 读这个流程、表单的草稿，记下版本 */
  async function open(): Promise<DesignDraft | null> {
    const key = options.key();
    opened = key ? await flow.designDraft(options.kind, key) : null;
    rev = opened?.rev ?? null;
    revKey = opened ? key : null;
    return opened;
  }

  return {
    status: autosave.status,
    open,

    /** 编辑器载入好了：现在的内容是基准，之后改了才存 */
    ready() {
      autosave.start(opened);
    },

    /** 放弃草稿：后端删掉；编辑器载入已发布的版本后再 ready */
    async discard() {
      autosave.stop();
      const key = options.key();
      if (key) await flow.discardDesignDraft(options.kind, key);
      rev = null;
      revKey = null;
      opened = null;
    },

    /** 冲突后读别人存的草稿：编辑器换成它的内容后再 ready，接着它改 */
    async reload(): Promise<DesignDraft | null> {
      autosave.stop();
      return open();
    },

    /** 发布成功：后端已经删了草稿，之后再改重新开一份 */
    published() {
      rev = null;
      revKey = null;
      opened = null;
      autosave.start();
    },
  };
}
