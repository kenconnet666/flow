// 设计器用扩展目录：校验认得注册的名字，自动节点、自定义节点的出边能选声明的结果，条件里能用计算值
import { validateProcess, type ProcessDefinition } from '@zerodep-dev/flow-model';
import { describe, expect, it } from 'vitest';
import type { ExtensionCatalog, ExtensionEntry } from '../src/client/types';
import { conditionFields } from '../src/admin/process-designer/EdgePanel';
import { declaredOutcomes, registeredIn } from '../src/admin/process-designer/extensions';

const entry = (name: string, extra: Partial<ExtensionEntry> = {}): ExtensionEntry => ({
  name,
  label: name,
  description: null,
  params: [],
  outcomes: [],
  valueType: null,
  ...extra,
});

const catalog: ExtensionCatalog = {
  resolvers: [entry('leader')],
  guards: [],
  actions: [entry('push', { outcomes: [{ value: 'accepted', label: '对方接收' }] })],
  computed: [entry('amountLevel', { label: '金额档位', valueType: 'number' })],
  nodes: [entry('x-check', { outcomes: [{ value: 'pass', label: '通过' }] })],
  completions: [],
  fieldTypes: [],
  nodeMeta: [],
  formServerCode: true,
};

const definition: ProcessDefinition = {
  key: 'p',
  name: 'p',
  nodes: [
    { id: 'start', type: 'start', name: '提交' },
    { id: 'check', type: 'x-check', name: '核验' },
    { id: 'push', type: 'service', name: '推送', action: 'push' },
    { id: 'pick', type: 'task', name: '审批', assignees: [{ resolver: 'nobody' }], outcomes: [{ key: 'ok', label: '同意' }] },
    { id: 'end', type: 'end', name: '结束' },
  ],
  edges: [
    { id: 'e1', from: 'start', to: 'check' },
    { id: 'e2', from: 'check', to: 'push', outcome: 'pass' },
    { id: 'e3', from: 'push', to: 'pick', outcome: 'accepted' },
    { id: 'e4', from: 'pick', to: 'end', outcome: 'ok' },
  ],
};

describe('扩展目录', () => {
  it('校验认得注册的名字，没注册的报出来；自定义节点的出边写结果不报错', () => {
    const problems = validateProcess(definition, { isRegistered: registeredIn(catalog) });
    expect(problems.filter((p) => p.code === 'extension-unknown').map((p) => p.params)).toEqual([{ kind: 'resolver', name: 'nobody' }]);
    expect(problems.some((p) => p.code === 'edge-outcome-not-branching')).toBe(false);
  });

  it('自动节点、自定义节点的结果取注册时声明的', () => {
    expect(declaredOutcomes(catalog, definition.nodes[1]!)).toEqual([{ value: 'pass', label: '通过' }]);
    expect(declaredOutcomes(catalog, definition.nodes[2]!)).toEqual([{ value: 'accepted', label: '对方接收' }]);
    expect(declaredOutcomes(null, definition.nodes[2]!)).toEqual([]);
  });

  it('条件里能用计算值 fn.名字，按声明的类型比较', () => {
    const fields = conditionFields(definition, null, '表单', (n) => n, catalog.computed, '计算值');
    expect(fields).toEqual([{ name: 'fn.amountLevel', label: '金额档位', type: 'number', group: '计算值' }]);
  });
});
