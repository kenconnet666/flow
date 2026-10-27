/**
 * 示例的端到端测试：真实浏览器（Playwright 的 Chromium）点示例前端，接真实的示例后端。
 * 先起示例后端（8080，发布过采购流程：`pnpm seed`）与示例前端（`pnpm dev`，5180），再在 typescript/apps/demo 下 `pnpm e2e`。
 * 前端不在 http://localhost:5180 时设 E2E_URL；有界面地看设 E2E_HEADED=1。每次用带时刻的标题，不依赖库里原有的数据。
 */
import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { chromium, type Browser, type Page } from 'playwright';

const BASE = process.env.E2E_URL ?? 'http://localhost:5180';
const STARTER = 1001; // 林小满：门店，发起采购
const FINANCE = 1002; // 周启航：财务复核
const BOSS = 1004; // 总经理：大额审批

let browser: Browser;

before(async () => {
  browser = await chromium.launch({ headless: !process.env.E2E_HEADED });
});

after(async () => {
  await browser?.close();
});

/** 以某个人打开一页：示例的"登录"是存在 localStorage 里的当前用户 */
async function open(user: number, path: string, width = 1280): Promise<Page> {
  const context = await browser.newContext({ viewport: { width, height: 900 } });
  await context.addInitScript((id) => localStorage.setItem('flow-demo:user', String(id)), user);
  const page = await context.newPage();
  page.on('pageerror', (error) => assert.fail(`page error on ${path}: ${error.message}`));
  await page.goto(BASE + path);
  return page;
}

/** 发起一张采购单，回到待办；返回标题 */
async function startPurchase(amount: string, width?: number): Promise<string> {
  const title = `E2E 采购 ${Date.now()}`;
  const page = await open(STARTER, '/start/purchase', width);
  await page.getByLabel('标题').fill(title);
  await page.getByLabel('金额').fill(amount);
  await page.getByRole('button', { name: '提交', exact: true }).click();
  await page.waitForURL('**/inbox');
  await page.getByText(`已发起：采购申请：${title}`).waitFor();
  await page.context().close();
  return title;
}

/** 在待办里打开这一单，点一个处理结果；回到待办 */
async function handle(user: number, title: string, outcome: string, comment?: string): Promise<void> {
  const page = await open(user, '/inbox');
  await page.getByRole('link', { name: `采购申请：${title}` }).click();
  await page.waitForURL('**/work-items/*');
  if (comment) await page.getByLabel('意见', { exact: true }).fill(comment);
  await page.getByRole('button', { name: outcome, exact: true }).click();
  await page.waitForURL('**/inbox');
  await page.getByText('已处理').waitFor();
  assert.equal(await page.getByRole('link', { name: `采购申请：${title}` }).count(), 0, '处理完不在待办里了');
  await page.context().close();
}

describe('采购审批', () => {
  test('大额：财务复核通过，总经理同意，发起人在我发起的里看到已完成', async () => {
    const title = await startPurchase('12000');
    await handle(FINANCE, title, '通过');
    await handle(BOSS, title, '同意');

    const page = await open(STARTER, '/started');
    const row = page.getByRole('row', { name: new RegExp(title) });
    await row.getByText('已完成').waitFor();
    await row.getByRole('link', { name: `采购申请：${title}` }).click();
    await page.waitForURL('**/instances/*');
    await page.getByText('总经理审批').first().waitFor();
    await page.context().close();
  });

  test('否决要填意见；填了之后流程结束在否决', async () => {
    const title = await startPurchase('300');
    const page = await open(FINANCE, '/inbox');
    await page.getByRole('link', { name: `采购申请：${title}` }).click();
    await page.waitForURL('**/work-items/*');
    await page.getByRole('button', { name: '否决', exact: true }).click();
    await page.locator('[aria-invalid="true"], [role="alert"]').first().waitFor();
    assert.match(page.url(), /work-items/, '没填意见不能否决，还在这一页');
    await page.getByLabel('意见', { exact: true }).fill('超预算');
    await page.getByRole('button', { name: '否决', exact: true }).click();
    await page.waitForURL('**/inbox');
    await page.context().close();

    const started = await open(STARTER, '/started');
    await started.getByRole('row', { name: new RegExp(title) }).getByText('已完成').waitFor();
    await started.context().close();
  });

  test('手机宽度：发起与办理都不出横向滚动条', async () => {
    const title = await startPurchase('500', 375);
    const page = await open(FINANCE, '/inbox', 375);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth), 375);
    await page.getByRole('link', { name: `采购申请：${title}` }).click();
    await page.waitForURL('**/work-items/*');
    await page.getByRole('button', { name: '通过', exact: true }).waitFor();
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth), 375);
    await page.context().close();
  });
});

describe('列表的筛选与排序', () => {
  test('我发起的：只看已归档、按标题排序都发到后端', async () => {
    const page = await open(STARTER, '/started');
    await page.getByRole('row').nth(1).waitFor();
    const sent: string[] = [];
    page.on('request', (request) => request.url().includes('/flow/queries/started') && sent.push(request.url()));

    await page.getByRole('columnheader', { name: /标题/ }).getByRole('button').first().click();
    await page.getByLabel('只看已归档').check();
    await page.getByRole('button', { name: '查询', exact: true }).click();
    await page.getByText('暂无数据').waitFor();

    const last = new URL(sent.at(-1)!);
    assert.equal(last.searchParams.get('archived'), 'true');
    assert.ok(last.searchParams.get('sort')?.includes('title'), last.search);
    await page.context().close();
  });
});
