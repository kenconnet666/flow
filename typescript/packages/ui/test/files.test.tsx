// 附件：接入方经 provideFlowFiles 给怎样上传、打开；表单的值只是传完了的 { id, name, size }
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { describe, expect, it } from 'vitest';
import type { FormView } from '../src/client/types';
import type { FlowFormHandle } from '../src/form/context';
import { provideFlowFiles, type FileRef, type FileSource } from '../src/form/files';
import { FlowForm } from '../src/form/FlowForm';
import { mount, settle } from './mount';

const view: FormView = {
  versionId: 1n,
  key: 'expense-form',
  version: 1,
  manifest: { receipts: { label: '票据', type: 'file', maxCount: 3 } },
  source: null,
  compiled: null,
  compilerVersion: null,
  access: { default: 'editable' },
  start: true,
  values: { receipts: [{ id: 'f1', name: '发票.pdf', size: 1200 }] },
};

/** 挂一张带附件的表单；source 为 null 时不给附件的存取 */
async function mountWith(source: FileSource | null, mode: 'fill' | 'view' = 'fill') {
  let handle: FlowFormHandle | undefined;
  const Host = _component(() => {
    if (source) provideFlowFiles(source);
    return () => <FlowForm view={view} mode={mode} onReady={(form) => (handle = form)} />;
  });
  const host = mount(() => <Host />);
  await settle();
  await settle();
  return { host, handle: handle! };
}

const uploads: File[] = [];
const source: FileSource = {
  // 慢一点传完：中间表单会先收到"还没有传完的"，不能把上传中的那个清掉
  upload: async (file) => {
    await new Promise((resolve) => setTimeout(resolve, 20));
    uploads.push(file);
    return { id: `f${uploads.length + 1}`, name: file.name, size: file.size };
  },
  href: (file) => `/files/${file.id}`,
};

describe('附件', () => {
  it('已有的附件列出来，文件名链到 href；表单的值原样是 { id, name, size }', async () => {
    const { host, handle } = await mountWith(source);
    const link = host.querySelector('a') as HTMLAnchorElement;
    expect(link.textContent).toContain('发票.pdf');
    expect(link.getAttribute('href')).toBe('/files/f1');
    expect(handle.values()['receipts']).toEqual([{ id: 'f1', name: '发票.pdf', size: 1200 }]);
  });

  it('选了文件就经 FileSource 上传，传完的加进表单的值', async () => {
    const { host, handle } = await mountWith(source);
    const input = host.querySelector('input[type="file"]') as HTMLInputElement;
    const file = new File(['abc'], '车票.png', { type: 'image/png' });
    Object.defineProperty(input, 'files', { value: [file], configurable: true });
    input.dispatchEvent(new Event('change', { bubbles: true }));
    await settle();
    expect(host.textContent).toContain('车票.png');
    await new Promise((resolve) => setTimeout(resolve, 40));
    await settle();
    expect(uploads).toContain(file);
    expect((handle.values()['receipts'] as FileRef[]).map((f) => f.name)).toEqual(['发票.pdf', '车票.png']);
  });

  it('还没传完时校验不放行，提示附件在上传；传完了放行，附件在值里', async () => {
    // 由测试决定什么时候传完
    let finish: (ref: FileRef) => void = () => {};
    const held: FileSource = { upload: () => new Promise<FileRef>((resolve) => (finish = resolve)) };
    const { host, handle } = await mountWith(held);
    const input = host.querySelector('input[type="file"]') as HTMLInputElement;
    const file = new File(['abc'], '住宿单.pdf', { type: 'application/pdf' });
    Object.defineProperty(input, 'files', { value: [file], configurable: true });
    input.dispatchEvent(new Event('change', { bubbles: true }));
    await settle();
    expect(await handle.validate()).toBe(false);
    await settle();
    expect(host.textContent).toContain('附件还在上传');

    finish({ id: 'f9', name: '住宿单.pdf', size: 3 });
    await settle();
    await settle();
    expect(host.textContent).not.toContain('附件还在上传');
    expect(await handle.validate()).toBe(true);
    expect((handle.values()['receipts'] as FileRef[]).map((f) => f.name)).toEqual(['发票.pdf', '住宿单.pdf']);
  });

  it('没给附件的存取时只显示文件名，不能上传', async () => {
    const { host } = await mountWith(null);
    expect(host.textContent).toContain('发票.pdf');
    expect(host.querySelector('input[type="file"]')).toBeNull();
  });
});
