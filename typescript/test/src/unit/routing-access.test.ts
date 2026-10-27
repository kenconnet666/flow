import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
  applyEffects,
  fieldStates,
  mergeSubmission,
  processGraph,
  sameValue,
  selectEdges,
  type Effect,
  type FieldState,
  type FormManifest,
  type NodeFormAccess,
  type ProcessDefinition,
} from '@zerodep-dev/flow-model';
import { fromWire } from '@zerodep-dev/json-wire';
import { readSpec } from './spec.js';

interface RoutingSpec {
  cases: { name: string; definitionFile: string; node: string; outcome?: string; values?: Record<string, unknown>; expect: string[] }[];
  effects: { name: string; effects: Effect[]; variables: Record<string, unknown>; values?: Record<string, unknown>; expect: Record<string, unknown> }[];
}

interface AccessSpec {
  manifest: FormManifest;
  cases: { name: string; access?: NodeFormAccess; isStart?: boolean; values: Record<string, unknown>; states: Record<string, unknown[]> }[];
  merges: {
    name: string;
    access?: NodeFormAccess;
    isStart?: boolean;
    values: Record<string, unknown>;
    stored: Record<string, unknown>;
    submitted: Record<string, unknown>;
    expectValues: Record<string, unknown>;
    expectErrors: { field: string; code: string }[];
  }[];
}

/** 两份变量相等：值按 sameValue 比（Java 一侧小数是 BigDecimal，这里是数字或文字），数组、对象逐项比 */
function sameVariables(actual: unknown, expected: unknown): boolean {
  if (Array.isArray(expected)) return Array.isArray(actual) && actual.length === expected.length && expected.every((v, i) => sameVariables(actual[i], v));
  if (expected !== null && typeof expected === 'object') {
    const a = (actual ?? {}) as Record<string, unknown>;
    const keys = new Set([...Object.keys(a), ...Object.keys(expected)]);
    return [...keys].every((k) => sameVariables(a[k], (expected as Record<string, unknown>)[k]));
  }
  return expected === null ? actual === null || actual === undefined : sameValue(actual, expected);
}

describe('选出边（spec/routing）', () => {
  const spec = readSpec<RoutingSpec>('routing/select-edges.json');
  for (const sample of spec.cases) {
    it(sample.name, () => {
      const definition = readSpec<{ definition: ProcessDefinition }>(sample.definitionFile).definition;
      const values = sample.values ?? {};
      const edges = selectEdges(processGraph(definition), sample.node, { outcome: sample.outcome ?? null, read: (f) => values[f] });
      assert.deepEqual(edges.map((e) => e.id), sample.expect);
    });
  }
  for (const sample of spec.effects) {
    it(`副作用：${sample.name}`, () => {
      const values = sample.values ?? {};
      const actual = applyEffects(sample.effects, sample.variables, (f) => values[f]);
      assert.ok(sameVariables(actual, sample.expect), JSON.stringify(actual));
    });
  }
});

/** FieldState → 样例里的 [显示, 能改, 必填, 原因] */
const tuple = (s: FieldState) => (s.visible ? [s.visible, s.editable, s.required] : [s.visible, s.editable, s.required, s.hiddenBy]);

describe('字段权限（spec/access）', () => {
  const spec = readSpec<AccessSpec>('access/field-access.json');
  for (const sample of spec.cases) {
    it(sample.name, () => {
      const states = fieldStates(spec.manifest, { access: sample.access, isStart: sample.isStart, read: (f) => sample.values[f] });
      assert.deepEqual(Object.fromEntries([...states].map(([k, s]) => [k, tuple(s)])), sample.states);
    });
  }
  // 值同线上：每个用例按自己的  还原
  for (const sample of spec.merges.map((m) => fromWire<AccessSpec['merges'][number]>(m))) {
    it(`合并：${sample.name}`, () => {
      const states = fieldStates(spec.manifest, { access: sample.access, isStart: sample.isStart, read: (f) => sample.values[f] });
      const result = mergeSubmission(spec.manifest, states, sample.stored, sample.submitted);
      assert.deepEqual(result.values, sample.expectValues);
      assert.deepEqual(result.errors, sample.expectErrors);
    });
  }
});
