import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { compareValues, evaluateCondition, isConditionComplete, type Condition } from '@zerodep-dev/flow-model';
import { readSpec } from './spec.js';

interface ConditionSpec {
  values: Record<string, unknown>;
  cases: { name: string; values?: Record<string, unknown>; condition: Condition; expect: boolean }[];
  complete: { name: string; condition: Condition; expect: boolean }[];
}

const spec = readSpec<ConditionSpec>('conditions/rules.json');

/** spec/conditions：两个后端共用的样例 */
describe('条件求值（spec/conditions）', () => {
  for (const sample of spec.cases) {
    it(sample.name, () => {
      const values = sample.values ?? spec.values;
      assert.equal(evaluateCondition(sample.condition, (field) => values[field]), sample.expect);
    });
  }
  for (const sample of spec.complete) {
    it(`写完整：${sample.name}`, () => assert.equal(isConditionComplete(sample.condition), sample.expect));
  }
});

describe('值的比较', () => {
  it('大整数与长小数按精确的小数比', () => {
    assert.equal(compareValues(12345678901234567890n, '12345678901234567890.0'), 0);
    assert.ok(compareValues('0.1', '0.10000000000000001') < 0);
  });

  it('负零等于零，-0.5 小于 0', () => {
    assert.equal(compareValues('-0', 0), 0);
    assert.ok(compareValues('-0.5', 0) < 0);
  });

  it('不是数字时按编码顺序比', () => {
    assert.ok(compareValues('B', 'a') < 0);
    assert.ok(compareValues('2026-09-30', '2026-10-01') < 0);
  });
});
