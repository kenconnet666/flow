// 建表脚本只在 Java flow-core 的 resources 里维护一份：改了脚本后跑 `node scripts/sync-sql.mjs`，生成 src/schema/mysql-scripts.ts。
// 生成成 TS 模块而不是运行时读文件：应用经 Nitro、Vite 打包后 import.meta.url 变了，按相对路径找不到文件。
// 单元测试 schema.test 比对两边，忘了同步时失败。
import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const from = fileURLToPath(new URL('../../../../java/flow-core/src/main/resources/db/flow/mysql/', import.meta.url));
const to = fileURLToPath(new URL('../src/schema/mysql-scripts.ts', import.meta.url));

const version = (file) => Number(/^V(\d+)__/.exec(file)[1]);
const scripts = readdirSync(from)
  .filter((file) => /^V\d+__.+\.sql$/.test(file))
  .sort((a, b) => version(a) - version(b))
  .map((file) => `  { file: ${JSON.stringify(file)}, sql: ${JSON.stringify(readFileSync(from + file, 'utf8').replaceAll('\r\n', '\n'))} },`);

writeFileSync(
  to,
  `// 由 scripts/sync-sql.mjs 从 Java flow-core 的建表脚本（db/flow/mysql）生成，不要手改\n` +
    `/** MySQL 的建表脚本，按版本号排 */\n` +
    `export const MYSQL_SCRIPTS: readonly { readonly file: string; readonly sql: string }[] = [\n${scripts.join('\n')}\n];\n`,
);
