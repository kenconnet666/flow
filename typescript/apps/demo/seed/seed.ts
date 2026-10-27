/**
 * 给示例后端发布示例用的表单与流程：采购（spec/scenarios 里那一份，表单只有清单，按清单自动排版）、
 * 请假（表单是代码：源码 leave-form.tsx，清单从源码里取出）、扩展演示（用示例后端注册的扩展：自定义节点 x-demo-check、计算值 demo.amountLevel、
 * 解析器 demo.leader、提交前检查 demo.amount-cap、动作 demo.push，表单借采购申请单）。在 typescript/apps/demo 下 `pnpm seed`；
 * 后端不在 http://localhost:8080 时设 FLOW_BACKEND。同一份再发布一次就是新版本。
 */
import { readFileSync } from 'node:fs';
import { extractFields } from '../../../packages/ui/src/form-tools/extract.ts';

const backend = process.env.FLOW_BACKEND ?? 'http://localhost:8080';
const here = (path: string) => new URL(path, import.meta.url);
const json = (path: string) => JSON.parse(readFileSync(here(path), 'utf8')) as Record<string, unknown>;

/** 以林小满（1001）的身份调接口；出错时停下 */
async function post(path: string, body: unknown): Promise<unknown> {
  const response = await fetch(`${backend}/flow${path}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', 'x-user': '1001' },
    body: JSON.stringify(body),
  });
  const result = (await response.json()) as { code: string; data?: unknown; errors?: unknown };
  if (result.code !== 'ok') throw new Error(`${path}: ${result.code} ${JSON.stringify(result.errors ?? '')}`);
  return result.data;
}

const leaveSource = readFileSync(here('leave-form.tsx'), 'utf8');
const { manifest, problems } = extractFields(leaveSource);
if (!manifest) throw new Error(`leave-form.tsx: ${JSON.stringify(problems)}`);

console.log(await post('/forms/publish', json('../../../../spec/scenarios/forms/purchase-form.json')));
console.log(await post('/definitions/publish', json('../../../../spec/scenarios/processes/purchase.json')));
console.log(await post('/forms/publish', { key: 'leave-form', name: '请假单', manifest, clientSource: leaveSource }));
console.log(await post('/definitions/publish', json('leave.json')));
console.log(await post('/definitions/publish', json('ext-demo.json')));
