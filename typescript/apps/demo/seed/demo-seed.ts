/**
 * 演示的种子数据：顶栏的"重置演示"与 `pnpm seed` 共用这一份。先由示例后端清空引擎的数据（POST /demo/reset），再按档发布：
 * - basic（从头开始）：示例流程——采购申请（spec/scenarios 那一份，表单只有清单）、请假（代码表单，清单从源码里取）、
 *   扩展演示（用示例后端注册的扩展）。照着搭建教程从零画。
 * - tutorials（教程做完）：再加上六个教程画出的流程与报销单（tutorials.json，截图跑完后用 export-tutorials.ts 从示例后端导出），
 *   以及几单在办、办完的单子，直接试日常使用、协作与管理。
 * 文件由调用方读好交进来（Node 读文件、浏览器由 Vite 打包），这里只管顺序、以谁的身份做。
 */
import { stringifyWire } from '@zerodep-dev/json-wire';
import Decimal from 'decimal.js';
import { extractFields } from '../../../packages/ui/src/form-tools/extract.ts';

/** 两档 */
export type DemoPreset = 'basic' | 'tutorials';

/** 种子用到的文件 */
export interface SeedFiles {
  purchaseForm: object;
  purchase: object;
  /** 请假单的源码（TSX） */
  leaveForm: string;
  leave: object;
  extDemo: object;
  /** 教程做完那一档：表单发布的请求体、流程定义，各按发布的顺序 */
  tutorials: { forms: object[]; processes: object[] };
}

/** 示例里的人（同 session.ts） */
const STARTER = 1001;
const FINANCE = 1002;
const BOSS = 1004;

/** 命令的结果里用到的部分（编号在线上是文字） */
interface CommandData {
  instance: { id: string };
  workItems: { id: string; node: string }[];
}

/**
 * 清空并发布。backend 是示例后端的地址（浏览器里同源时写空串）。
 * 一档里任何一步失败就抛错：清空之后的数据不完整，再点一次重置即可。
 */
export async function seedDemo(backend: string, preset: DemoPreset, files: SeedFiles): Promise<void> {
  async function post<T = unknown>(path: string, body: unknown, user = BOSS): Promise<T> {
    const response = await fetch(`${backend}${path}`, {
      method: 'POST',
      headers: { 'content-type': 'application/json', 'x-user': String(user) },
      body: stringifyWire(body),
    });
    const result = (await response.json()) as { code: string; data?: T; errors?: unknown; message?: string };
    if (result.code !== 'ok') throw new Error(`${path}: ${result.code} ${result.message ?? ''} ${JSON.stringify(result.errors ?? '')}`);
    return result.data as T;
  }

  await post('/demo/reset', {});

  const { manifest, problems } = extractFields(files.leaveForm);
  if (!manifest) throw new Error(`leave-form.tsx: ${JSON.stringify(problems)}`);
  await post('/flow/forms/publish', files.purchaseForm);
  await post('/flow/definitions/publish', files.purchase);
  await post('/flow/forms/publish', { key: 'leave-form', name: '请假单', manifest, clientSource: files.leaveForm });
  await post('/flow/definitions/publish', files.leave);
  await post('/flow/definitions/publish', files.extDemo);
  if (preset === 'basic') return;

  for (const form of files.tutorials.forms) await post('/flow/forms/publish', form);
  for (const process of files.tutorials.processes) await post('/flow/definitions/publish', process);

  const start = (key: string, form: Record<string, unknown>) => post<CommandData>('/flow/commands/start', { key, form }, STARTER);
  // 在办：财务的收件箱里待认领（办理、转办、委派、加签都拿它试）
  await start('purchase', { title: '办公椅 20 把', amount: new Decimal('16000'), quantity: 20 });
  await start('expense', { title: '去上海见客户', amount: new Decimal('8000'), category: '差旅' });
  await start('review', { title: '服务器采购', amount: new Decimal('120000'), quantity: 4 });
  await start('contract', { title: '年度维保合同', amount: new Decimal('50000') });
  // 停在"等签署回执"，等外部系统送事件（教程 6）
  await start('esign', { title: '供应商框架协议', amount: new Decimal('100') });
  // 办完的：财务通过，不到一万直接结束
  const done = await start('purchase', { title: '打印机墨盒', amount: new Decimal('600'), quantity: 2 });
  const review = done.workItems[0]!;
  await post('/flow/commands/claim', { workItemId: review.id }, FINANCE);
  await post('/flow/commands/complete', { workItemId: review.id, outcome: 'pass', comment: '型号核对过了' }, FINANCE);
}
