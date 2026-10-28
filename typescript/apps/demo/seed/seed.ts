/**
 * 在命令行重置演示（同示例前端顶栏的"重置演示"）：清空引擎的数据，按档发布示例的流程与表单（见 demo-seed.ts）。
 * 在 typescript/apps/demo 下 `pnpm seed`（从头开始）或 `pnpm seed tutorials`（教程做完）；后端不在 http://localhost:8080 时设 FLOW_BACKEND。
 * 加 --if-empty 时库里已经有流程就什么都不做：部署的演示站点每次启动都跑一遍，只在第一次（空库）时发布。
 * 部署时它被打成一个文件（deploy/demo/build.sh），种子文件平铺在 FLOW_SEED_DIR 里。
 */
import { readFileSync } from 'node:fs';
import { basename, join } from 'node:path';
import { seedDemo, type DemoPreset } from './demo-seed.ts';

const backend = process.env.FLOW_BACKEND ?? 'http://localhost:8080';
const seedDir = process.env.FLOW_SEED_DIR;
const args = process.argv.slice(2);
const preset = (args.find((arg) => !arg.startsWith('--')) ?? 'basic') as DemoPreset;
if (preset !== 'basic' && preset !== 'tutorials') throw new Error(`unknown preset ${preset}: basic or tutorials`);

if (args.includes('--if-empty')) {
  const response = await fetch(`${backend}/flow/queries/processes`, { headers: { 'x-user': '1004' } });
  const result = (await response.json()) as { code: string; data?: unknown[] };
  if (result.code !== 'ok') throw new Error(`/flow/queries/processes: ${result.code}`);
  if (result.data!.length > 0) {
    console.log('已有流程，不重置');
    process.exit(0);
  }
}

/** 种子文件：path 是在仓库里相对本文件的位置；设了 FLOW_SEED_DIR 时按文件名到那里读 */
const text = (path: string) => readFileSync(seedDir ? join(seedDir, basename(path)) : new URL(path, import.meta.url), 'utf8');
const json = (path: string) => JSON.parse(text(path)) as object;

await seedDemo(backend, preset, {
  purchaseForm: json('../../../../spec/scenarios/forms/purchase-form.json'),
  purchase: json('../../../../spec/scenarios/processes/purchase.json'),
  leaveForm: text('leave-form.tsx'),
  leave: json('leave.json'),
  extDemo: json('ext-demo.json'),
  erpPush: json('erp-push.json'),
  tutorials: json('tutorials.json') as { forms: object[]; processes: object[] },
});
console.log(`演示已重置：${preset}`);
