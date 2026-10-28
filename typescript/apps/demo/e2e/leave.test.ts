/**
 * 请假（代码表单）的端到端测试：病假才出现的附件字段，上传到示例后端（/demo/files），提交后在流程详情里能打开。
 * 运行条件同 purchase.test.ts（示例后端、示例前端都起着，`pnpm seed` 发布过带附件字段的请假单）。
 */
import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { chromium, type Browser } from 'playwright';

// 演示的页面都在 /app 下
const BASE = `${process.env.E2E_URL ?? 'http://localhost:5180'}/app`;
const STARTER = 1001;

let browser: Browser;

before(async () => {
  browser = await chromium.launch({ headless: !process.env.E2E_HEADED });
});

after(async () => {
  await browser?.close();
});

test('病假带证明材料：上传、提交，流程详情里能下载', async () => {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await context.addInitScript((id) => localStorage.setItem('flow-demo:user', String(id)), STARTER);
  const page = await context.newPage();
  page.on('pageerror', (error) => assert.fail(`page error: ${error.message}`));
  await page.goto(`${BASE}/start/leave`);

  assert.equal(await page.getByText('证明材料').count(), 0, '年假、没选时不要证明');
  await page.getByRole('combobox', { name: '假别' }).click();
  await page.getByRole('option', { name: '病假' }).click();
  await page.getByLabel('天数').fill('3');
  const name = `诊断证明-${Date.now()}.txt`;
  // 等传完再提交：没传完时提交会被拦下（"附件还在上传"），后端刚启动时第一次上传要慢一些
  const uploaded = page.waitForResponse((response) => response.url().includes('/demo/files') && response.request().method() === 'POST');
  await page.locator('input[type="file"]').setInputFiles({ name, mimeType: 'text/plain', buffer: Buffer.from('诊断证明的内容') });
  await uploaded;
  await page.getByText('已上传').waitFor();
  // 标题每次都是"请假：3 天"，从发起的响应里取实例编号直接打开，不在列表里认
  const started = page.waitForResponse((response) => response.url().includes('/flow/commands/start'));
  await page.getByRole('button', { name: '提交', exact: true }).click();
  const body = (await (await started).json()) as { data: { instance: { id: string } } };
  await page.waitForURL('**/inbox');

  await page.goto(`${BASE}/instances/${body.data.instance.id}`);
  const link = page.getByRole('link', { name });
  const href = await link.getAttribute('href');
  assert.ok(href?.startsWith('/demo/files/'), href ?? 'no link');
  const download = await page.request.get(new URL(href!, page.url()).href);
  assert.equal(download.status(), 200);
  assert.equal(await download.text(), '诊断证明的内容');
  await context.close();
});
