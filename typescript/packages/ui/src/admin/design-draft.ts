/**
 * 设计中的草稿（0004 的 5.2）：流程编辑器、表单编辑器共用。改了停一会儿自动保存到后端，带着版本防两个人互相覆盖；
 * 打开时有草稿就接着草稿改，发布后草稿由后端删掉。
 *
 *   const draft = useDesignDraft({ kind: 'process', key: () => processKey, content: currentJson, … });
 *   const opened = await draft.open();      // 有草稿时返回它（编辑器换成草稿的内容），没有时 null
 *   draft.ready();                         // 编辑器载入好了：从这一刻起改了就存
 *   draft.published();                     // 发布成功：草稿没了，之后再改重新开一份
 */
import { HttpError } from '@zerodep-dev/vue-tsx-pro-ui';
import { onBeforeUnmount, shallowReactive, watch } from 'vue';
import { useFlowClient } from '../client/flow-client';
import type { DesignDraft, DraftKind } from '../client/types';

/** 改完停多久保存（毫秒） */
const SETTLE = 2000;

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

/** 草稿现在怎样 */
export interface DesignDraftStatus {
  /** idle 没改过；saving 正在存；saved 存好了；conflict 别人先存过，我的没存；failed 存的时候出错 */
  state: 'idle' | 'saving' | 'saved' | 'conflict' | 'failed';
  /** 最近一次存好的草稿 */
  saved: DesignDraft | null;
  /** 冲突时先存的那个人 */
  conflictWith: string | null;
}

/** 草稿的自动保存 */
export function useDesignDraft(options: DesignDraftOptions) {
  const flow = useFlowClient();
  const status = shallowReactive<DesignDraftStatus>({ state: 'idle', saved: null, conflictWith: null });
  let rev: number | null = null;
  // 和这份内容一样时不用存：载入的已发布版本、草稿，或刚存过的
  let baseline: string | null = null;
  let timer: ReturnType<typeof setTimeout> | undefined;

  async function save() {
    const key = options.key();
    const content = options.content();
    if (!key || content === baseline || status.state === 'conflict') return;
    status.state = 'saving';
    try {
      const saved = await flow.saveDesignDraft({
        kind: options.kind,
        key,
        name: options.name(),
        content,
        baseVersion: options.baseVersion(),
        rev,
      });
      rev = saved.rev;
      baseline = content;
      status.saved = saved;
      status.state = 'saved';
    } catch (error) {
      if (error instanceof HttpError && error.code === 'flow.concurrent-modification') {
        status.state = 'conflict';
        status.conflictWith = String(error.params['updatedBy'] ?? '') || null;
      } else {
        status.state = 'failed';
      }
    }
  }

  watch(options.content, () => {
    if (baseline === null) return;
    clearTimeout(timer);
    timer = setTimeout(() => void save(), SETTLE);
  });
  onBeforeUnmount(() => clearTimeout(timer));

  return {
    status,

    /** 打开时读草稿：有时记下它的版本并返回（编辑器换成它的内容） */
    async open(): Promise<DesignDraft | null> {
      const key = options.key();
      if (!key) return null;
      const draft = await flow.designDraft(options.kind, key);
      rev = draft?.rev ?? null;
      status.saved = draft;
      return draft;
    },

    /** 编辑器载入好了：现在的内容是基准，之后改了才存 */
    ready() {
      baseline = options.content();
    },

    /** 放弃草稿：后端删掉，回到已发布的版本由编辑器自己载入 */
    async discard() {
      const key = options.key();
      clearTimeout(timer);
      if (key) await flow.discardDesignDraft(options.kind, key);
      rev = null;
      baseline = null;
      Object.assign(status, { state: 'idle', saved: null, conflictWith: null });
    },

    /** 冲突后载入别人存的草稿，接着它改 */
    async reload(): Promise<DesignDraft | null> {
      Object.assign(status, { state: 'idle', conflictWith: null });
      const draft = await this.open();
      baseline = draft?.content ?? options.content();
      return draft;
    },

    /** 发布成功：后端已经删了草稿，之后再改重新开一份 */
    published() {
      clearTimeout(timer);
      rev = null;
      baseline = options.content();
      Object.assign(status, { state: 'idle', saved: null, conflictWith: null });
    },
  };
}
