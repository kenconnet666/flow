// 后端没把 /flow 的响应写成 typed JSON 时（编号是普通数字）提示一次；配好时（编号是 bigint）不说；数据原样返回
import { describe, expect, it } from 'vitest';
import { createWireCheck, WIRE_MISSING } from '../src/client/wire-check';

describe('createWireCheck', () => {
  it('编号还原成 bigint 时不提示，数据原样返回', () => {
    const reports: string[] = [];
    const check = createWireCheck((message) => reports.push(message));
    const page = { total: 1, rows: [{ id: 9007199254740993n, instance: { id: 3n } }] };
    expect(check(page)).toBe(page);
    expect(reports).toEqual([]);
  });

  it('列表行、嵌套对象里的编号是普通数字时提示，只说一次', () => {
    const reports: string[] = [];
    const check = createWireCheck((message) => reports.push(message));
    check({ total: 1, rows: [{ id: 7, title: '报销' }] });
    check({ workItem: { id: 8 } });
    expect(reports).toEqual([WIRE_MISSING]);
  });

  it('没有编号的结果（可发起的流程、空列表）不提示', () => {
    const reports: string[] = [];
    const check = createWireCheck((message) => reports.push(message));
    check([{ key: 'purchase', name: '采购申请', version: 1 }]);
    check({ total: 0, rows: [] });
    check(undefined);
    expect(reports).toEqual([]);
  });
});
