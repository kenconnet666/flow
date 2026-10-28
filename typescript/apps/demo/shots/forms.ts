/** 表单：在表单设计器里画"报销单"（字段、选项、明细表、显示条件、预览），换给教程 1 的报销流程、配字段权限，再出新版本 */
import type { Page } from 'playwright';
import { discardDraft, node, open, shot, USERS } from './lib.ts';

/** 表单设计器左边的字段组件、右边的字段属性 */
const paletteOf = (page: Page) => page.getByRole('group', { name: '组件' }).getByRole('group', { name: '字段', exact: true });
const inspectorOf = (page: Page) => page.getByRole('group', { name: '字段', exact: true }).last();

export async function run() {
  await create();
  await bind();
  await newVersion();
}

/** 新建报销单（自带标题）：金额、选项、显示条件、明细表、预览、代码与后端代码页签，发布 */
async function create() {
  await discardDraft('form', 'expense-form');
  let page = await open(USERS.boss, '/admin/forms');
  await shot(page, 'forms/01-list', { mark: page.getByRole('button', { name: /新建表单/ }) });
  await page.getByRole('button', { name: /新建表单/ }).click();
  await page.getByLabel('标识').fill('expense-form');
  await page.getByLabel('标题', { exact: true }).first().fill('报销单');
  await page.getByRole('button', { name: '设计', exact: true }).first().click();
  await page.waitForTimeout(1200);
  await shot(page, 'forms/02-designer', { mark: page.getByRole('group', { name: '组件' }) });

  const palette = paletteOf(page);
  const inspector = inspectorOf(page);
  async function add(type: string, name: string, label: string) {
    await palette.getByRole('button', { name: type, exact: true }).click();
    await page.waitForTimeout(300);
    await inspector.getByRole('textbox', { name: '字段名' }).fill(name);
    await inspector.getByRole('textbox', { name: '字段名' }).blur();
    await inspector.getByRole('textbox', { name: '标签' }).fill(label);
    await page.waitForTimeout(300);
  }
  // 新建的表单自带一个必填的标题（title）
  await add('金额', 'amount', '金额');
  await inspector.getByRole('switch', { name: '必填' }).check();
  await shot(page, 'forms/03-field', { mark: inspector });
  await add('选项', 'category', '费用类别');
  await inspector.getByRole('textbox', { name: '选项' }).fill(['差旅', '办公', '招待'].join(String.fromCharCode(10)));
  await inspector.getByRole('switch', { name: '必填' }).check();
  await shot(page, 'forms/04-options', { mark: inspector.getByRole('textbox', { name: '选项' }) });
  await add('人员', 'companions', '同行人');
  const show = page.getByRole('group', { name: '显示条件' });
  await show.getByRole('button', { name: '添加条件', exact: true }).click();
  const rule = show.getByRole('group', { name: '条件 1' });
  await rule.getByRole('combobox', { name: '字段' }).click();
  await page.getByRole('option', { name: '费用类别', exact: true }).click();
  await rule.getByRole('combobox').nth(2).click();
  await page.getByRole('option', { name: '差旅', exact: true }).click();
  await shot(page, 'forms/05-show-when', { mark: show });
  await add('附件', 'invoice', '发票');
  await palette.getByRole('button', { name: '明细表', exact: true }).click();
  await page.waitForTimeout(400);
  const list = page.getByRole('group', { name: '字段', exact: true }).last();
  await list.getByRole('textbox', { name: '字段名' }).first().fill('items');
  await list.getByRole('textbox', { name: '字段名' }).first().blur();
  await list.getByRole('textbox', { name: '标签' }).first().fill('费用明细');
  const col1 = list.getByRole('group', { name: '列 1' });
  await col1.getByRole('textbox', { name: '字段名' }).fill('item');
  await col1.getByRole('textbox', { name: '字段名' }).press('Enter');
  await col1.getByRole('textbox', { name: '标签' }).fill('项目');
  await list.getByRole('button', { name: '加一列' }).click();
  const col2 = list.getByRole('group', { name: '列 2' });
  await col2.getByRole('textbox', { name: '字段名' }).fill('money');
  await col2.getByRole('textbox', { name: '字段名' }).press('Enter');
  await col2.getByRole('combobox', { name: '控件' }).click();
  await page.getByRole('option', { name: '金额' }).click();
  await col2.getByRole('textbox', { name: '标签' }).fill('金额');
  await shot(page, 'forms/06-list', { mark: list.getByRole('group', { name: '列', exact: true }) });
  await add('多行文字', 'note', '财务意见');
  await page.getByRole('button', { name: '预览', exact: true }).click();
  await page.waitForTimeout(800);
  await page.getByRole('combobox', { name: '费用类别' }).click();
  await page.getByRole('option', { name: '差旅' }).click();
  await page.getByRole('button', { name: /添加费用明细/ }).click();
  await shot(page, 'forms/07-preview', { wait: 600 });
  await page.getByRole('button', { name: '代码', exact: true }).click();
  await shot(page, 'forms/08-code', { wait: 1200 });
  await page.getByRole('tab', { name: '后端代码' }).click();
  await shot(page, 'forms/09-backend', { wait: 1000 });
  await page.getByRole('tab', { name: '表单' }).click();
  await page.getByLabel('发布说明').fill('首次发布');
  await page.getByRole('button', { name: '发布', exact: true }).click();
  await page.getByRole('region', { name: '通知' }).getByText(/已发布/).waitFor();
  await page.context().close();
}

/** 换给报销流程，配字段权限 */
async function bind() {
  // 换给报销流程，配字段权限：发起时财务意见隐藏，财务审核时可编辑
  const page = await open(USERS.boss, '/admin/processes/expense');
  await page.getByRole('button', { name: '发布', exact: true }).waitFor();
  await page.waitForTimeout(1000);
  await page.getByRole('combobox', { name: '表单' }).click();
  await page.getByRole('option', { name: /报销单/ }).click();
  await page.waitForTimeout(600);
  await shot(page, 'forms/10-bind', { mark: page.getByRole('combobox', { name: '表单' }) });
  await node(page, '提交').click();
  await page.getByRole('group', { name: '表单字段' }).getByRole('combobox', { name: '财务意见' }).click();
  await page.getByRole('option', { name: '隐藏' }).click();
  await node(page, '财务审核').click();
  const access = page.getByRole('group', { name: '表单字段' });
  await access.getByRole('combobox', { name: '财务意见' }).click();
  await page.getByRole('option', { name: '可编辑' }).click();
  await shot(page, 'forms/11-access', { clip: access });
  await page.getByLabel('发布说明').fill('换成报销单');
  await page.getByRole('button', { name: '发布', exact: true }).click();
  await page.getByRole('region', { name: '通知' }).getByText(/已发布/).waitFor();
  await page.context().close();
}

/** 出新版本 */
async function newVersion() {
  // 表单出新版本：编辑器提示还在用旧版本的流程，一键重新发布
  let page = await open(USERS.boss, '/admin/forms/expense-form');
  const palette = paletteOf(page);
  const inspector = inspectorOf(page);
  await page.getByRole('button', { name: '发布', exact: true }).waitFor();
  await page.waitForTimeout(1500);
  await page.getByRole('button', { name: '设计', exact: true }).first().click();
  await page.waitForTimeout(800);
  await palette.getByRole('button', { name: '日期', exact: true }).click();
  await page.waitForTimeout(300);
  await inspector.getByRole('textbox', { name: '字段名' }).fill('spentOn');
  await inspector.getByRole('textbox', { name: '字段名' }).blur();
  await inspector.getByRole('textbox', { name: '标签' }).fill('发生日期');
  await page.getByLabel('发布说明').fill('加发生日期');
  await page.getByRole('button', { name: '发布', exact: true }).click();
  await page.getByText(/还在用旧表单/).waitFor();
  await shot(page, 'forms/12-outdated', { mark: page.getByText(/还在用旧表单/).locator('xpath=..') });
  await page.getByRole('button', { name: /重新发布这/ }).click();
  await page.getByText(/已换成第/).waitFor();
  await page.context().close();
  page = await open(USERS.starter, '/start/expense');
  await page.getByRole('combobox', { name: '费用类别' }).click();
  await page.getByRole('option', { name: '差旅' }).click();
  await shot(page, 'forms/13-start', { wait: 800 });
  await page.context().close();
}

/** 调试用：只跑一段 */
export const parts = { create, bind, newVersion };
