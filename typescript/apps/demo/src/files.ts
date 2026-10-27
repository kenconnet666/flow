/**
 * 示例的附件存取：上传到示例后端的 /demo/files（ExampleFiles，只为演示），经组件库配置的请求客户端发（带上当前用户、
 * 按 typed JSON 还原响应）。真实应用换成自己的文件服务。
 */
import type { FileRef, FileSource } from '@zerodep-dev/flow-ui/form';
import { scopeHttp, type HttpClient } from '@zerodep-dev/vue-tsx-pro-ui';

export function demoFiles(http: HttpClient): FileSource {
  const api = scopeHttp(http, '/demo');
  return {
    upload: async (file, { signal }) => {
      const body = new FormData();
      body.append('file', file);
      const stored = await api.post<{ id: string; name: string; size: bigint | number }>('/files', body, { signal });
      return { id: stored.id, name: stored.name, size: Number(stored.size) } satisfies FileRef;
    },
    href: (file) => `/demo/files/${encodeURIComponent(file.id)}`,
  };
}
