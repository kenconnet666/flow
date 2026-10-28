/** 管理：流程管理与版本、流程监控与管理员操作（暂停、跳转、改派、改数据、迁移）、异常处理、工作日历、统计 */
import { open, shot, USERS } from './lib.ts';

export async function run() {
  let page = await open(USERS.boss, '/admin/processes');
  await shot(page, 'admin/01-processes', { mark: page.getByRole('row', { name: /报销/ }).first().getByRole('cell').last() });
  await page.goto(page.url().replace(/\/admin\/processes$/, '/admin/processes/expense'));
  await page.getByRole('button', { name: '发布', exact: true }).waitFor();
  await page.getByRole('combobox', { name: '载入版本' }).click();
  await shot(page, 'admin/02-versions', { mark: page.getByRole('listbox').last(), wait: 600 });
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: /对比/ }).click();
  await shot(page, 'admin/03-diff', { wait: 1200 });
  await page.context().close();

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

  for (const [path, name] of [['/admin/incidents', '11-incidents'], ['/admin/calendars', '12-calendars'], ['/admin/stats', '13-stats']] as const) {
    page = await open(USERS.boss, path);
    await shot(page, `admin/${name}`, { wait: 1200 });
    await page.context().close();
  }
}
