/**
 * 在命令行重置演示（同示例前端顶栏的"重置演示"）：清空引擎的数据，按档发布示例的流程与表单（见 demo-seed.ts）。
 * 在 typescript/apps/demo 下 `pnpm seed`（从头开始）或 `pnpm seed tutorials`（教程做完）；后端不在 http://localhost:8080 时设 FLOW_BACKEND。
 * 部署的演示站点可以用它定时重置。
 */
import { readFileSync } from 'node:fs';
import { seedDemo, type DemoPreset } from './demo-seed.ts';

const backend = process.env.FLOW_BACKEND ?? 'http://localhost:8080';
const preset = (process.argv[2] ?? 'basic') as DemoPreset;
if (preset !== 'basic' && preset !== 'tutorials') throw new Error(`unknown preset ${preset}: basic or tutorials`);

const text = (path: string) => readFileSync(new URL(path, import.meta.url), 'utf8');
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
