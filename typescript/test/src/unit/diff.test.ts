import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { diffProcess, type ProcessDefinition, type ProcessDiff } from '@zerodep-dev/flow-model';
import { readSpec } from './spec.js';

interface DiffSpec {
  cases: { name: string; layout?: boolean; before: ProcessDefinition; after: ProcessDefinition; expect: ProcessDiff }[];
}

describe('两个版本的差别（spec/diff）', () => {
  for (const sample of readSpec<DiffSpec>('diff/versions.json').cases) {
    it(sample.name, () => assert.deepEqual(diffProcess(sample.before, sample.after, { layout: sample.layout === true }), sample.expect));
  }
});
