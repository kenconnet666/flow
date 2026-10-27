/**
 * 扩展的端到端测试：示例后端 extension 包里注册的扩展（自定义节点 x-demo-check、计算值 demo.amountLevel、解析器 demo.leader、
 * 提交前检查 demo.amount-cap、动作 demo.push）在设计器里认得出，在流程里按它们走。开头自己发布一次 seed/ext-demo.json，不依赖 pnpm seed。
 */
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { after, before, describe, test } from 'node:test';
import { chromium, type Browser, type Page } from 'playwright';

const BASE = process.env.E2E_URL ?? 'http://localhost:5180';
const STARTER = 1001; // 林小满：发起
const BOSS = 1004; // 总经理：解析器"发起人的上级"往上 2 级落到他

let browser: Browser;

before(async () => {
  browser = await chromium.launch({ headless: !process.env.E2E_HEADED });
  const definition = readFileSync(new URL('../seed/ext-demo.json', import.meta.url), 'utf8');
  const response = await fetch(`${BASE}/flow/definitions/publish`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', 'x-user': String(BOSS) },
    body: definition,
  });
  assert.equal(((await response.json()) as { code: string }).code, 'ok', '发布扩展演示');
});

after(async () => {
  await browser?.close();
});

async function open(user: number, path: string): Promise<Page> {
  const context = await browser.newContext({ viewport: { width: 1440, height: 960 } });
  await context.addInitScript((id) => localStorage.setItem('flow-demo:user', String(id)), user);
  const page = await context.newPage();
  page.on('pageerror', (error) => assert.fail(`page error on ${path}: ${error.message}`));
  await page.goto(BASE + path);
  return page;
}

/** 发起一单，返回实例编号 */
async function start(amount: string): Promise<{ id: string; title: string }> {
  const title = `E2E 扩展 ${Date.now()}`;
  const page = await open(STARTER, '/start/ext-demo');
  await page.getByLabel('标题').fill(title);
  await page.getByLabel('金额').fill(amount);
  const started = page.waitForResponse((response) => response.url().includes('/flow/commands/start'));
  await page.getByRole('button', { name: '提交', exact: true }).click();
  const body = (await (await started).json()) as { data: { instance: { id: string } } };
  await page.waitForURL('**/inbox');
  await page.context().close();
  return { id: body.data.instance.id, title };
}

/** 实例详情上的状态 */
async function statusOf(id: string): Promise<string> {
  const page = await open(STARTER, `/instances/${id}`);
  await page.getByText(/进行中|已完成/).first().waitFor();
  const text = await page.getByText(/进行中|已完成/).first().textContent();
  await page.context().close();
  return text ?? '';
}

describe('扩展', () => {
  test('设计器认得注册的扩展：自定义节点在节点列表里，解析器与动作显示注册的名字', async () => {
    const page = await open(BOSS, '/admin/processes/ext-demo');
    await page.getByRole('button', { name: '添加外部核验' }).waitFor();
    await page.getByText('发起人的上级').first().waitFor();
    await page.getByText('推送外部系统').first().waitFor();
    await page.getByText('没有发现问题').waitFor();
    await page.context().close();
  });

  test('大额按计算值走到上级审批，解析器落到总经理，同意后推送并走完', async () => {
    const { id, title } = await start('25000');
    const page = await open(BOSS, '/inbox');
    await page.getByRole('link', { name: `扩展演示：${title}` }).click();
    await page.waitForURL('**/work-items/*');
    await page.getByRole('button', { name: '同意', exact: true }).click();
    await page.waitForURL('**/inbox');
    await page.context().close();
    // 推送是异步的自动节点：作业做完实例才结束
    for (let i = 0; i < 20 && (await statusOf(id)) !== '已完成'; i++) await new Promise((resolve) => setTimeout(resolve, 500));
    assert.equal(await statusOf(id), '已完成');
  });

  test('超过一百万时提交前检查拦下同意', async () => {
    const { title } = await start('2000000');
    const page = await open(BOSS, '/inbox');
    await page.getByRole('link', { name: `扩展演示：${title}` }).click();
    await page.waitForURL('**/work-items/*');
    await page.getByRole('button', { name: '同意', exact: true }).click();
    await page.getByText('金额超过一百万，不能按这个结果提交').waitFor();
    assert.match(page.url(), /work-items/, '被拦下，还在这一页');
    await page.context().close();
  });

  test('小额不经人工审批直接推送走完', async () => {
    const { id } = await start('300');
    for (let i = 0; i < 20 && (await statusOf(id)) !== '已完成'; i++) await new Promise((resolve) => setTimeout(resolve, 500));
    assert.equal(await statusOf(id), '已完成');
  });
});
