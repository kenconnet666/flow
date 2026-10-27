/**
 * 发布不覆盖别人的：流程编辑器、表单编辑器共用。记着打开时看到的最新版本（新建时 0），发布时带上（后端的 expectedLatest）；
 * 这期间标识被别人占了、或者别人又发布过，后端报 flow.concurrent-modification，这里换成说清谁发布了第几版的话。
 *
 *   const guard = usePublishGuard(!processKey, () => versions.data);
 *   await flow.publishDefinition(json, note, guard.expected());   // 成功后 guard.published(result.version)
 *   failure = publishConflictText(error, messages) ?? errorText(error);
 */
import { HttpError } from '@zerodep-dev/vue-tsx-pro-ui';
import { ref, watch } from 'vue';
import type { FlowMessages } from '../../lang/messages';

/** 记着编辑时看到的最新版本 */
export function usePublishGuard(isNew: boolean, versions: () => readonly { readonly version: number }[] | null | undefined) {
  const latest = ref<number | null>(isNew ? 0 : null);
  watch(versions, (list) => {
    if (list?.length) latest.value = list[0]!.version;
  }, { immediate: true });
  return {
    /** 发布时带上的；还不知道时不带（后端不核对） */
    expected: (): number | undefined => latest.value ?? undefined,
    /** 发布成功：自己发布的这一版就是最新的 */
    published: (version: number) => {
      latest.value = version;
    },
  };
}

/** 发布时撞了别人的：标识被占了、或打开后别人又发布过；别的错误为 null */
export function publishConflictText(error: unknown, messages: FlowMessages): string | null {
  if (!(error instanceof HttpError) || error.code !== 'flow.concurrent-modification') return null;
  const reason = error.params['reason'];
  if (reason !== 'key-taken' && reason !== 'newer-version') return null;
  return messages.publishConflict(reason, Number(error.params['latest']), String(error.params['publishedBy'] ?? '') || null);
}
