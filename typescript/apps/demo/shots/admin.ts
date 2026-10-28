/** 管理：流程管理与版本、流程监控与管理员操作（暂停、跳转、改派、改数据、迁移）、异常处理、工作日历、统计 */
import { readFileSync } from 'node:fs';
import { addIncident, seedPost } from '../seed/demo-seed.ts';
import { BACKEND, discardDraft, node, open, shot, USERS } from './lib.ts';

export async function run() {
  let page = await open(USERS.boss, '/admin/processes');
  await shot(page, 'admin/01-processes', { mark: page.getByRole('row', { name: /报销/ }).first().getByRole('cell').last() });
  await page.goto(page.url().replace(/\/admin\/processes$/, '/admin/processes/expense'));
  await page.getByRole('button', { name: '发布', exact: true }).waitFor();
  await page.getByRole('combobox', { name: '载入版本' }).click();
  await shot(page, 'admin/02-versions', { mark: page.getByRole('listbox').last(), wait: 600 });
  await page.keyboard.press('Escape');
  // 对比的是现在的改动与载入的那一版：先改一处（节点改名）再对比；截完放弃这份草稿
  await node(page, '财务审核').click();
  await page.getByLabel('名称', { exact: true }).fill('财务初审');
  await page.getByRole('button', { name: /对比/ }).click();
  await shot(page, 'admin/03-diff', { mark: page.getByText(/^与 v\d+ 相比/).locator('xpath=..'), wait: 1200 });
  await page.context().close();
  await discardDraft('process', 'expense');

  // 流程监控：找一单进行中的，打开详情做管理员操作
  page = await open(USERS.boss, '/admin/monitor');
  await shot(page, 'admin/04-monitor', { wait: 800 });
  await page.getByLabel('标题').first().fill('打印机墨盒');
  await page.getByRole('button', { name: '查询' }).click();
  await page.waitForTimeout(800);
  await page.getByRole('link', { name: /打印机墨盒/ }).first().click();
  await page.waitForURL('**/instances/*');
  await page.getByText('管理员操作').waitFor();
  await shot(page, 'admin/05-actions', { mark: page.getByText('管理员操作').locator('xpath=..'), wait: 800 });
  for (const [button, name] of [['跳转', '06-jump'], ['改派', '07-reassign'], ['改数据', '08-change-data'], ['迁移版本', '09-migrate']] as const) {
    await page.getByRole('button', { name: button, exact: true }).first().click();
    const dialog = page.getByRole('dialog').last();
    await dialog.waitFor();
    await shot(page, `admin/${name}`, { clip: dialog, wait: 600 });
    await dialog.getByRole('button', { name: '取消' }).click();
    await dialog.waitFor({ state: 'hidden' });
  }
  await page.getByRole('button', { name: '暂停', exact: true }).click();
  await page.getByText('已暂停').first().waitFor();
  await shot(page, 'admin/10-suspended', { mark: page.getByRole('button', { name: '恢复', exact: true }) });
  await page.getByRole('button', { name: '恢复', exact: true }).click();
  await page.context().close();

  // 异常：发起一单"推送 ERP（演示异常）"，等作业轮询跑到它（示例后端 5 秒一次）
  const erpPush = JSON.parse(readFileSync(new URL('../seed/erp-push.json', import.meta.url), 'utf8')) as object;
  await addIncident(seedPost(BACKEND), erpPush);
  page = await open(USERS.boss, '/admin/incidents');
  const incident = page.getByRole('row', { name: /对接 ERP/ });
  for (let i = 0; i < 20 && (await incident.count()) === 0; i++) {
    await page.waitForTimeout(1000);
    await page.reload();
    await page.waitForLoadState('networkidle');
  }
  await shot(page, 'admin/11-incidents', { mark: incident, wait: 800 });
  await page.context().close();

  page = await open(USERS.boss, '/admin/calendars');
  await shot(page, 'admin/12-calendars', { wait: 1200 });
  await page.context().close();

  // 统计：选一个流程，看各环节的处理
  page = await open(USERS.boss, '/admin/stats');
  await page.getByRole('combobox', { name: '流程' }).click();
  await page.getByRole('option', { name: '采购申请', exact: true }).click();
  await shot(page, 'admin/13-stats', { wait: 1200 });
  await page.context().close();
}
