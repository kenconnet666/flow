import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { checkFieldValue, validateManifest, type FieldDefinition, type FormManifest, type Problem } from '@zerodep-dev/flow-model';
import { listSpec, readSpec } from './spec.js';

interface ManifestSpec {
  name: string;
  manifest: FormManifest;
  context?: { fieldTypes?: string[] };
  expect: Problem[];
}

interface ValuesSpec {
  cases: { name: string; field: FieldDefinition; value: unknown; expect: [string, string][] }[];
}

/** 比较时不看先后，同 validate.test.ts */
const normalize = (problems: readonly Problem[]) =>
  problems.map((p) => JSON.stringify([p.code, p.severity, p.field ?? null, p.params ?? null])).sort();

describe('字段清单的校验（spec/manifests）', () => {
  for (const file of listSpec('manifests')) {
    const sample = readSpec<ManifestSpec>(`manifests/${file}`);
    it(`${file}：${sample.name}`, () => {
      const types = sample.context?.fieldTypes;
      const actual = validateManifest(sample.manifest, types ? { isFieldType: (t) => types.includes(t) } : {});
      if (process.env.FLOW_SPEC_PRINT) console.log(file, JSON.stringify(actual, null, 1));
      assert.deepEqual(normalize(actual), normalize(sample.expect));
    });
  }
});

describe('值的校验（spec/values）', () => {
  for (const sample of readSpec<ValuesSpec>('values/field-values.json').cases) {
    it(sample.name, () => {
      assert.deepEqual(checkFieldValue(sample.field, sample.value).map((e) => [e.path, e.code]), sample.expect);
    });
  }
});
