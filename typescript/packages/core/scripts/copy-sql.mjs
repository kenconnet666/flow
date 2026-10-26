// 打包前把建表脚本抄进本包的 sql/mysql：脚本只在 Java flow-core 的 resources 里维护一份，两个后端用同一份
import { cpSync, rmSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const from = fileURLToPath(new URL('../../../../java/flow-core/src/main/resources/db/flow/mysql/', import.meta.url));
const to = fileURLToPath(new URL('../sql/mysql/', import.meta.url));
rmSync(to, { recursive: true, force: true });
cpSync(from, to, { recursive: true });
