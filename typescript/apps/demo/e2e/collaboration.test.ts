/**
 * 协作与设计时防覆盖的端到端测试：委派出去的人只给意见（不预测下一步），办完回到原来的人手里；
 * 新建流程填了已有的标识，草稿不存、发布被拦，打开原来那个流程的人看不到这份草稿。运行条件同 purchase.test.ts。
 */
import assert from 'node:assert/strict';
import { after, before, describe, test } from 'node:test';
import { chromium, type Browser, type Locator, type Page } from 'playwright';

// 演示的页面都在 /app 下
const BASE = `${process.env.E2E_URL ?? 'http://localhost:5180'}/app`;
const STARTER = 1001; // 林小满：发起采购
const FINANCE = 1002; // 周启航：财务复核（财务角色，进收件箱）
const HELPER = 1005; // 许知远：被委派来给意见
const ADMIN = 1004; // 总经理：管理端

let browser: Browser;

before(async () => {
  browser = await chromium.launch({ headless: !process.env.E2E_HEADED });
});

after(async () => {
  await browser?.close();
});

async function open(user: number, path: string): Promise<Page> {
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await context.addInitScript((id) => localStorage.setItem('flow-demo:user', String(id)), user);
  const page = await context.newPage();
  page.on('pageerror', (error) => assert.fail(`page error on ${path}: ${error.message}`));
  await page.goto(BASE + path);
  return page;
}

/** 在待办里打开这一单 */
async function openItem(user: number, title: string): Promise<Page> {
  const page = await open(user, '/inbox');
  await page.getByRole('link', { name: `采购申请：${title}` }).click();
  await page.waitForURL('**/work-items/*');
  return page;
}

/** 在对话框里点开选人，搜到这个人勾上 */
async function pickPerson(page: Page, dialog: Locator, trigger: string, person: string): Promise<void> {
  await dialog.getByRole('button', { name: trigger }).click();
  const picker = page.getByRole('dialog', { name: '选择人员' });
  await picker.getByRole('textbox', { name: /搜索/ }).fill(person);
  await picker.getByRole('checkbox', { name: person }).first().check();
  await picker.getByRole('button', { name: '确定' }).click();
  await picker.waitFor({ state: 'hidden' });
}

describe('协作', () => {
  test('委派：被委派的人只给意见、不预测下一步，办完回到原来的人手里，再由他通过', async () => {
    const title = `E2E 委派 ${Date.now()}`;
    const start = await open(STARTER, '/start/purchase');
    await start.getByLabel('标题').fill(title);
    await start.getByLabel('金额').fill('800');
    await start.getByRole('button', { name: '提交', exact: true }).click();
    await start.waitForURL('**/inbox');
    await start.context().close();

    let page = await openItem(FINANCE, title);
    await page.getByRole('button', { name: '委派', exact: true }).click();
    const delegate = page.getByRole('dialog', { name: '委派' });
    await pickPerson(page, delegate, '交给谁', '许知远');
    await delegate.getByLabel('说明').fill('帮我核一下报价');
    await delegate.getByRole('button', { name: '确定' }).click();
    await delegate.waitFor({ state: 'hidden' });
    await page.context().close();

    page = await openItem(HELPER, title);
    await page.getByText('周启航 请你给意见').waitFor();
    assert.equal(await page.getByText(/通过 →/).count(), 0, '意见不决定往下走，不预测下一步');
    await page.getByLabel('意见', { exact: true }).fill('报价合理');
    await page.getByRole('button', { name: '通过', exact: true }).click();
    await page.waitForURL('**/inbox');
    await page.context().close();

    page = await openItem(FINANCE, title);
    await page.getByText('报价合理').waitFor();
    await page.getByRole('button', { name: '通过', exact: true }).click();
    await page.waitForURL('**/inbox');
    await page.context().close();

    const started = await open(STARTER, '/started');
    await started.getByRole('row', { name: new RegExp(title) }).getByText('已通过').waitFor();
    await started.context().close();
  });
});

describe('设计时不覆盖别人的', () => {
  test('新建流程填了已有的标识：草稿不存、发布被拦，打开原来的流程看不到这份草稿', async () => {
    const page = await open(ADMIN, '/admin/processes/new');
    await page.getByLabel('流程标识').fill('purchase');
    await page.getByLabel('名称', { exact: true }).fill('不该覆盖采购');
    // 自动保存草稿时就说清楚，不等到发布
    await page.getByText('这个标识已经被用了').first().waitFor();
    await page.getByRole('button', { name: '发布', exact: true }).click();
    // 发布被拦：草稿那一行之外又多一条提示
    await page.getByText('这个标识已经被用了').nth(1).waitFor();
    await page.context().close();

    const existing = await open(ADMIN, '/admin/processes/purchase');
    await existing.getByRole('button', { name: '发布', exact: true }).waitFor();
    await existing.getByLabel('名称', { exact: true }).waitFor();
    assert.notEqual(await existing.getByLabel('名称', { exact: true }).inputValue(), '不该覆盖采购');
    assert.equal(await existing.getByText(/存的草稿改/).count(), 0, '没有载入别人的草稿');
    await existing.context().close();
  });
});
