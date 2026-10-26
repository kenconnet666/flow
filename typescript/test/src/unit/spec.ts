/** 读仓库根目录 spec/ 下两个后端共用的样例（编译后在 typescript/test/dist/unit/ 下运行） */
import { readFileSync } from 'node:fs';

const SPEC = new URL('../../../../spec/', import.meta.url);

/** 读一份 JSON 样例，路径相对 spec/ */
export function readSpec<T>(path: string): T {
  return JSON.parse(readFileSync(new URL(path, SPEC), 'utf8')) as T;
}
