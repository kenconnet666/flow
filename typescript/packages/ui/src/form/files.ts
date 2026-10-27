/**
 * 附件（字段类型 file，0004 的表格）：文件本身由接入方存，表单的值只记 `{ id, name, size }`。接入方在根组件里给一次怎样上传、
 * 怎样打开：
 *
 *   provideFlowFiles({
 *     upload: (file, { signal }) => http.post('/api/files', formData(file), { signal }),   // 返回 { id, name, size }
 *     href: (file) => `/api/files/${file.id}`,
 *   });
 *
 * 没给时附件字段只显示文件名（不能上传）。
 */
import { inject, provide, type InjectionKey } from 'vue';

/** 表单里的一个附件：存在接入方那里的文件 */
export interface FileRef {
  readonly id: string;
  readonly name: string;
  readonly size: number;
}

/** 附件的存取：接入方实现 */
export interface FileSource {
  /** 上传一个文件，返回存好之后的标识；报告进度、响应中止（换了文件、离开页面） */
  upload(file: File, context: { onProgress: (fraction: number) => void; signal: AbortSignal }): Promise<FileRef>;
  /** 打开、下载的地址；不写时文件名不是链接 */
  href?(file: FileRef): string;
}

const FLOW_FILES: InjectionKey<FileSource> = Symbol('flow-files');

/** 在根组件（或某一块页面）里给附件的存取 */
export function provideFlowFiles(source: FileSource): void {
  provide(FLOW_FILES, source);
}

/** 附件的存取；没给时为 undefined */
export function useFlowFiles(): FileSource | undefined {
  return inject(FLOW_FILES, undefined);
}

/** 附件字段报告自己还有几个没传完（选了、正在传）；所在的表单字段记下，提交时不放行 */
const UPLOAD_PENDING: InjectionKey<(count: number) => void> = Symbol('flow-upload-pending');

/** 表单字段（form.Field）给里面的附件控件一个报告的口子 */
export function provideUploadPending(report: (count: number) => void): void {
  provide(UPLOAD_PENDING, report);
}

/** 附件控件拿到报告的口子；不在表单字段里时为 undefined */
export function useUploadPending(): ((count: number) => void) | undefined {
  return inject(UPLOAD_PENDING, undefined);
}
