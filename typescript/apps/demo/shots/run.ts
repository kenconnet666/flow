/**
 * 重新截文档的配图：按教程在演示上真实操作，边做边截，图存 public/shots。
 * 先起示例后端（8080）与示例站点（5180），`pnpm seed` 重置成"从头开始"再跑；跑完 `pnpm seed:export` 导出"教程做完"那一档。
 *
 *   node shots/run.ts              # 全部
 *   node shots/run.ts build forms  # 只截这几组
 */
import * as admin from './admin.ts';
import * as build from './build.ts';
import * as forms from './forms.ts';
import * as use from './use.ts';
import { close, launch } from './lib.ts';

const GROUPS: Record<string, () => Promise<void>> = {
  // 有先后：合同审批在大额采购之前（大额采购调用它）；表单在教程 1 之后（把报销单换给报销流程）；管理用前面留下的数据
  build: async () => {
    await build.expense();
    await build.review();
    await build.contract();
    await build.trip();
    await build.bigbuy();
    await build.esign();
  },
  use: use.run,
  forms: forms.run,
  admin: admin.run,
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
