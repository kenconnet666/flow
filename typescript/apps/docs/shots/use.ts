/** 日常使用：发起、草稿、待办、办理、协作（转办、委派、加签）、我发起的、详情、留言、抄送、撤回一步；用种子里的"采购申请" */
import { BASE, handle, open, openItem, shot, USERS } from './lib.ts';

/** 发起一张采购申请，返回标题 */
async function startPurchase(title: string, amount: string) {
  const page = await open(USERS.starter, '/start/purchase');
  await page.getByLabel('标题').fill(title);
  await page.getByLabel('金额').fill(amount);
  await page.getByRole('button', { name: '提交', exact: true }).click();
  await page.waitForURL('**/inbox');
  await page.context().close();
}

/** 在对话框里点开选人，搜到这个人勾上 */
async function pickPerson(page: import('playwright').Page, dialog: import('playwright').Locator, trigger: RegExp, person: string) {
  await dialog.getByRole('button', { name: trigger }).click();
  const picker = page.getByRole('dialog', { name: '选择人员' });
  await picker.getByRole('textbox', { name: /搜索/ }).fill(person);
  await page.waitForTimeout(400);
  await picker.getByRole('checkbox', { name: person }).first().check();
  await picker.getByRole('button', { name: '确定' }).click();
  await picker.waitFor({ state: 'hidden' });
}

export async function run() {
  // 发起页、填单、草稿
  let page = await open(USERS.starter, '/');
  await shot(page, 'use/01-start-list', { mark: page.getByRole('link', { name: '采购申请' }) });
  await page.getByRole('link', { name: '采购申请' }).click();
  await page.getByLabel('标题').fill('办公椅 20 把');
  await page.getByLabel('金额').fill('16000');
  await page.getByLabel('数量').fill('20');
  await shot(page, 'use/02-start-form', { mark: page.getByText(/提交后到/) });
  await page.getByRole('button', { name: '暂存' }).click();
  await page.waitForTimeout(800);
  await page.goto(`${BASE}/`);
  await page.getByText('我的草稿').waitFor();
  await shot(page, 'use/03-drafts', { mark: page.getByText('我的草稿').locator('xpath=..') });
  await page.getByRole('button', { name: '接着填' }).first().click();
  await page.getByRole('button', { name: '提交', exact: true }).click();
  await page.waitForURL('**/inbox');
  await page.context().close();

  // 财务的待办：收件箱里待认领
  page = await open(USERS.financeA, '/inbox');
  await shot(page, 'use/04-inbox', { mark: page.getByRole('row', { name: /办公椅/ }) });
  await page.context().close();
  page = await openItem(USERS.financeA, '办公椅');
  await shot(page, 'use/05-item', { mark: page.getByRole('button', { name: '认领', exact: true }) });
  await page.getByRole('button', { name: '否决', exact: true }).click();
  await shot(page, 'use/06-comment-required', { mark: page.getByLabel('意见', { exact: true }) });
  await page.context().close();

  // 协作：周启航转给沈若水，沈若水委派许知远，许知远给意见，沈若水加签总经理
  page = await openItem(USERS.financeA, '办公椅');
  await page.getByRole('button', { name: '认领', exact: true }).click();
  await page.waitForTimeout(600);
  await page.getByRole('button', { name: '转办', exact: true }).click();
  const transfer = page.getByRole('dialog', { name: '转办' });
  await pickPerson(page, transfer, /交给谁/, '沈若水');
  await transfer.getByLabel('说明').fill('我这周出差，交给若水');
  await shot(page, 'use/07-transfer', { clip: transfer });
  await transfer.getByRole('button', { name: '确定' }).click();
  await page.waitForURL('**/inbox');
  await page.context().close();
  page = await openItem(USERS.financeB, '办公椅');
  await page.getByRole('button', { name: '委派', exact: true }).click();
  const delegate = page.getByRole('dialog', { name: '委派' });
  await pickPerson(page, delegate, /交给谁/, '许知远');
  await delegate.getByLabel('说明').fill('帮我核一下报价');
  await shot(page, 'use/08-delegate', { clip: delegate });
  await delegate.getByRole('button', { name: '确定' }).click();
  await page.waitForTimeout(1000);
  await page.context().close();
  page = await openItem(USERS.legal, '办公椅');
  await shot(page, 'use/09-opinion', { mark: page.getByText(/请你给意见/) });
  await page.context().close();
  await handle(USERS.legal, '办公椅', '通过', '报价合理');
  page = await openItem(USERS.financeB, '办公椅');
  await page.getByRole('button', { name: '加签', exact: true }).click();
  const sign = page.getByRole('dialog', { name: '加签' });
  await pickPerson(page, sign, /加哪些人/, '总经理');
  await sign.getByLabel('说明').fill('金额请总经理也看一下');
  await shot(page, 'use/10-add-sign', { clip: sign });
  await sign.getByRole('button', { name: '确定' }).click();
  await page.waitForTimeout(1000);
  await page.context().close();
  await handle(USERS.boss, '办公椅', '通过');
  await handle(USERS.financeB, '办公椅', '通过');

  // 我已办里撤回刚处理的一步（总经理审批还没办）
  page = await open(USERS.financeB, '/done');
  await page.getByRole('link', { name: /办公椅/ }).first().click();
  await page.waitForURL('**/work-items/*');
  await page.waitForTimeout(800);
  await shot(page, 'use/11-withdraw-step', { mark: page.getByRole('button', { name: /撤回这一步/ }) });
  await page.context().close();
  await handle(USERS.boss, '办公椅', '同意');

  // 我发起的、详情、留言 @、抄送、催办与撤回
  await startPurchase('打印机墨盒', '600');
  page = await open(USERS.starter, '/started');
  await shot(page, 'use/12-started', { mark: page.getByRole('row', { name: /办公椅/ }) });
  await page.getByRole('link', { name: /打印机墨盒/ }).click();
  await page.waitForURL('**/instances/*');
  await page.getByText('流转记录').waitFor();
  await shot(page, 'use/13-detail', { mark: [page.getByRole('button', { name: '催办' }), page.getByRole('button', { name: '撤回' })], wait: 1000 });
  await page.getByRole('textbox', { name: '留言' }).fill('墨盒型号是 HP 678，别买错');
  await page.getByRole('button', { name: '@ 提醒谁看' }).click();
  const mention = page.getByRole('dialog', { name: '@ 提醒谁看' });
  await mention.getByRole('textbox', { name: /搜索/ }).fill('周启航');
  await page.waitForTimeout(400);
  await mention.getByRole('checkbox', { name: '周启航' }).check();
  await mention.getByRole('button', { name: '确定' }).click();
  await mention.waitFor({ state: 'hidden' });
  await page.getByRole('button', { name: '留言', exact: true }).click();
  await page.waitForTimeout(800);
  await shot(page, 'use/14-comment', { clip: page.getByRole('textbox', { name: '留言' }).locator('xpath=ancestor::*[contains(., "流转记录")][1]') });
  await page.getByRole('button', { name: '抄送', exact: true }).click();
  const cc = page.getByRole('dialog', { name: '抄送' });
  await pickPerson(page, cc, /抄送给谁/, '许知远');
  await cc.getByLabel('附言').fill('知会一下');
  await shot(page, 'use/15-cc', { clip: cc });
  await cc.getByRole('button', { name: '确定' }).click();
  await page.waitForTimeout(800);
  await page.context().close();
  page = await open(USERS.legal, '/cc');
  await shot(page, 'use/16-cc-list', { wait: 800 });
  await page.context().close();
}
