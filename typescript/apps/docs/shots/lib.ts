/**
 * 截图脚本的工具：以某个用户打开示例前端、截图（可以先用红框圈出要点的元素、只截某一块）、设计器里的常用操作。
 * 图存到 public/shots/<分组>/<名字>.png，文档页用 <Shot src="分组/名字.png" />。
 */
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium, type Browser, type Locator, type Page } from 'playwright';

export const BASE = process.env.DEMO_URL ?? 'http://localhost:5180';
const OUT = new URL('../public/shots/', import.meta.url);

let browser: Browser;
export async function launch() {
  browser = await chromium.launch();
}
export async function close() {
  await browser.close();
}

/** 示例里的人：发起人、财务（两位）、总经理、法务（借许知远） */
export const USERS = { starter: 1001, financeA: 1002, financeB: 1003, boss: 1004, legal: 1005 } as const;

/** 以某个人打开一页（示例的"登录"是 localStorage 里的当前用户） */
export async function open(user: number, path: string, width = 1440, height = 900): Promise<Page> {
  const context = await browser.newContext({ viewport: { width, height }, deviceScaleFactor: 1 });
  await context.addInitScript((id) => localStorage.setItem('flow-demo:user', String(id)), user);
  const page = await context.newPage();
  page.on('pageerror', (e) => console.log('PAGEERROR', path, e.message));
  await page.goto(BASE + path);
  await page.waitForLoadState('networkidle');
  return page;
}

/** 截图的选项 */
export interface ShotOptions {
  /** 先用红框圈出这些元素（截完去掉） */
  mark?: Locator | Locator[];
  /** 只截这一块（外扩 pad 像素） */
  clip?: Locator;
  pad?: number;
  /** 截之前先等一会儿（动画、异步加载） */
  wait?: number;
}

/** 截一张图：name 是 "分组/名字"（不带 .png） */
export async function shot(page: Page, name: string, { mark, clip, pad = 12, wait = 400 }: ShotOptions = {}) {
  await page.waitForTimeout(wait);
  const marks = mark ? (Array.isArray(mark) ? mark : [mark]) : [];
  for (const m of marks) await m.first().evaluate((el) => el.setAttribute('data-shot-mark', ''));
  await page.addStyleTag({ content: '[data-shot-mark]{outline:3px solid #ef4444 !important;outline-offset:2px;border-radius:4px}' });
  const path = fileURLToPath(new URL(`${name}.png`, OUT));
  mkdirSync(dirname(path), { recursive: true });
  if (clip) {
    await clip.first().scrollIntoViewIfNeeded();
    await page.waitForTimeout(200);
    const box = (await clip.first().boundingBox())!;
    const view = page.viewportSize()!;
    const x = Math.max(0, box.x - pad);
    const y = Math.max(0, box.y - pad);
    await page.screenshot({ path, clip: { x, y, width: Math.min(view.width - x, box.width + pad * 2), height: Math.min(view.height - y, box.height + pad * 2) } });
  } else {
    await page.screenshot({ path });
  }
  for (const m of marks) await m.first().evaluate((el) => el.removeAttribute('data-shot-mark'));
  console.log('SHOT', name);
}

// ───────── 设计器里的操作 ─────────

/** 画布上的一个节点（按名字） */
export const node = (page: Page, name: string) => page.getByRole('button', { name: new RegExp(`^${name}（`) }).first();

/** 选中一条连线（键盘：聚焦后回车，与鼠标点线等效） */
export async function selectEdge(page: Page, label: string) {
  await page.getByRole('button', { name: label, exact: true }).focus();
  await page.keyboard.press('Enter');
  await page.waitForTimeout(200);
}

/** 在选人框里搜到一个人、角色或部门勾上 */
export async function pickIn(page: Page, dialog: Locator, name: string, byRole = false) {
  if (byRole) await dialog.getByRole('button', { name: '角色' }).click();
  else {
    await dialog.getByRole('textbox', { name: /搜索/ }).fill(name);
    await page.waitForTimeout(400);
  }
  await dialog.getByRole('checkbox', { name }).first().check();
}

/** 节点的处理人：去掉默认的"发起人"，选人或角色 */
export async function assignee(page: Page, name: string, byRole = false) {
  const who = page.getByRole('group', { name: '处理人', exact: true });
  await who.getByRole('checkbox', { name: '发起人' }).uncheck();
  await who.getByRole('button', { name: /处理人 选人/ }).click();
  const dialog = page.getByRole('dialog');
  await pickIn(page, dialog, name, byRole);
  await dialog.getByRole('button', { name: '确定' }).click();
  await dialog.waitFor({ state: 'hidden' });
}

/** 选中的连线上加一条比较条件（字段、比较方式、数值） */
export async function condition(page: Page, field: string, op: string, value: string) {
  const panel = page.getByRole('group', { name: '条件', exact: true });
  await panel.getByRole('button', { name: '添加条件', exact: true }).click();
  const rule = panel.getByRole('group', { name: '条件 1' });
  await rule.getByRole('combobox', { name: '字段' }).click();
  await page.getByRole('option', { name: field, exact: true }).click();
  await rule.getByRole('combobox', { name: '比较方式' }).click();
  await page.getByRole('option', { name: op, exact: true }).click();
  await rule.getByRole('spinbutton').fill(value);
}

/** 连线改接到别的节点 */
export async function retarget(page: Page, label: string, to: string) {
  await selectEdge(page, label);
  await page.getByRole('combobox', { name: '连到', exact: true }).click();
  await page.getByRole('option', { name: to }).click();
}

/** 放弃某个流程、表单的设计草稿（重跑脚本前清掉上次没发布完留下的） */
export async function discardDraft(kind: 'process' | 'form', key: string) {
  const backend = process.env.BACKEND_URL ?? 'http://localhost:8080';
  await fetch(`${backend}/flow/drafts/discard`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', 'x-user': String(USERS.boss) },
    body: JSON.stringify({ kind, key }),
  });
}

/** 新建流程：标识、名称、表单、实例标题 */
export async function newProcess(page: Page, key: string, name: string, form: RegExp, title: string) {
  await discardDraft('process', key);
  await page.getByLabel('流程标识').fill(key);
  await page.getByLabel('名称', { exact: true }).fill(name);
  await page.getByRole('combobox', { name: '表单' }).click();
  await page.getByRole('option', { name: form }).click();
  await page.getByLabel('实例标题').fill(title);
}

/** 自动排版并发布 */
export async function publish(page: Page, note: string) {
  await page.getByRole('button', { name: '自动排版' }).click();
  await page.waitForTimeout(400);
  await page.getByLabel('发布说明').fill(note);
  await page.getByRole('button', { name: '发布', exact: true }).click();
  await page.getByRole('region', { name: '通知' }).getByText(/已发布/).waitFor();
}

/** 以某人打开待办里标题含 title 的那一单 */
export async function openItem(user: number, title: string): Promise<Page> {
  const page = await open(user, '/inbox');
  await page.getByRole('link', { name: new RegExp(title) }).first().click();
  await page.waitForURL('**/work-items/*');
  await page.getByRole('button', { name: '暂存' }).waitFor();
  await page.waitForTimeout(800);
  return page;
}

/** 某人在待办里把这一单按 outcome 办掉 */
export async function handle(user: number, title: string, outcome = '同意', comment?: string) {
  const page = await openItem(user, title);
  if (comment) await page.getByLabel('意见', { exact: true }).fill(comment);
  await page.getByRole('button', { name: outcome, exact: true }).click();
  await page.waitForURL('**/inbox');
  await page.context().close();
}
