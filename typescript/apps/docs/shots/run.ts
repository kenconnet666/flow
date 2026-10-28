/**
 * 重新截文档的配图：按教程在示例前端上真实操作，边做边截，图存 public/shots。
 * 要一个干净的示例库：infra/mysql/reset-demo.sh 清空 → 重启示例后端（Flyway 建表）→ pnpm -C apps/demo seed → 起示例前端（5180）。
 *
 *   node shots/run.ts              # 全部
 *   node shots/run.ts build forms  # 只截这几组
 */
import * as build from './build.ts';
import { close, launch } from './lib.ts';

const GROUPS: Record<string, () => Promise<void>> = {
  // 教程有先后：合同审批要在大额采购之前（大额采购调用它）
  build: async () => {
    await build.expense();
    await build.review();
    await build.contract();
    await build.trip();
    await build.bigbuy();
    await build.esign();
  },
};

const wanted = process.argv.slice(2);
await launch();
try {
  for (const [name, run] of Object.entries(GROUPS)) {
    if (wanted.length && !wanted.includes(name)) continue;
    console.log('==', name);
    await run();
  }
} finally {
  await close();
}
