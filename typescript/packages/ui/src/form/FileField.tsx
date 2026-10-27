/**
 * 附件控件：组件库的 Upload（列表、进度、失败重试、移除、大小与类型检查）接上接入方的 FileSource。
 * 表单的值只是传完了的 `{ id, name, size }`；上传中、失败的只在控件里。在 form.Field 里时数据源、accept、maxCount、maxSize
 * 由字段补上（controls.ts），还有几个没传完也报给字段（提交时不放行，不然没传完的附件会悄悄丢掉）：
 *
 *   <form.Field name="attachments"><FileField /></form.Field>
 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { existingUploadFile, Upload, type UploadFile } from '@zerodep-dev/vue-tsx-pro-ui';
import { onBeforeUnmount, watch } from 'vue';
import { useUploadPending, type FileRef, type FileSource } from './files';

/** FileField 的属性 */
export interface FileFieldProps {
  /** 传完了的附件：父级写 _value={files} */
  _value?: FileRef[] | null;
  /** 怎样上传、打开；没有时只能看不能传 */
  source?: FileSource;
  /** 接受的文件类型（同原生 accept） */
  accept?: string;
  /** 最多几个；1 时新文件替换旧的 */
  maxCount?: number;
  /** 单个文件的大小上限（字节） */
  maxSize?: number;
  readonly?: boolean;
  disabled?: boolean;
}

/** 附件 */
export const FileField = _component(
  ({ _value: value = _bindable<FileRef[] | null>(null), source, accept, maxCount, maxSize, readonly, disabled }: FileFieldProps) => {
    // 已有的附件放进列表：response 记着它本身，传完了的 response 是 upload 的结果，取值时一样取
    const toList = (refs: readonly FileRef[] | null | undefined): UploadFile[] =>
      (refs ?? []).map((ref) => ({
        ...existingUploadFile({ id: ref.id, name: ref.name, size: ref.size, ...(source?.href ? { url: source.href(ref) } : {}) }),
        response: ref,
      }));
    let list = _state<UploadFile[]>(toList(value));
    // 自己写回的值不再读回来（读回来会丢掉上传中、失败的那几个）。按内容比：表单存的是一份拷贝，引用不同
    let written: readonly FileRef[] = [];
    const sameFiles = (a: readonly FileRef[] | null | undefined, b: readonly FileRef[]) =>
      (a ?? []).length === b.length && (a ?? []).every((file, i) => file.id === b[i]!.id);
    watch(
      () => value,
      (next) => {
        if (!sameFiles(next, written)) list = toList(next);
      },
    );

    const reportPending = useUploadPending();
    watch(
      () => list.filter((file) => file.status === 'selected' || file.status === 'uploading').length,
      (count) => reportPending?.(count),
      { immediate: true },
    );
    onBeforeUnmount(() => reportPending?.(0));

    function update(next: UploadFile[]) {
      list = next;
      written = next.filter((file) => file.status === 'done' && file.response).map((file) => file.response as FileRef);
      value = [...written];
    }

    return () => (
      <Upload
        _value={list}
        onUpdate:_value={update}
        {...(source ? { upload: (file: File, context: { onProgress: (fraction: number) => void; signal: AbortSignal }) => source.upload(file, context) } : {})}
        {...(accept ? { accept } : {})}
        {...(maxSize ? { maxSize } : {})}
        {...(maxCount ? { maxFiles: maxCount } : {})}
        multiple={maxCount !== 1}
        readonly={readonly === true || !source}
        disabled={disabled === true}
      />
    );
  },
);
