import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { DEFINITION_SCHEMA, definitionSchemaOf, parseDefinition, upgradeDefinition, validateProcess } from '@zerodep-dev/flow-model';

/** 定义的格式版本（同 Java 的 DefinitionSchemaTest）：不写是第 1 版；当前版照读；比当前还新的报出来 */
const definition = (schema?: unknown) => ({
  ...(schema === undefined ? {} : { schema }),
  key: 'p',
  name: 'p',
  nodes: [{ id: 'start', type: 'start', name: '提交' }],
  edges: [],
});

describe('定义的格式版本', () => {
  it('不写是第 1 版，当前版原样读', () => {
    assert.equal(definitionSchemaOf(definition()), 1);
    const current = definition(DEFINITION_SCHEMA);
    assert.equal(upgradeDefinition(current), current);
    assert.equal(parseDefinition(JSON.stringify(definition())).key, 'p');
  });

  it('比当前还新的读不了，校验报 schema-newer', () => {
    assert.throws(() => upgradeDefinition(definition(DEFINITION_SCHEMA + 1)), /newer than this version/);
    const problems = validateProcess(definition(DEFINITION_SCHEMA + 1) as never);
    assert.ok(problems.some((p) => p.code === 'schema-newer' && p.params?.['schema'] === DEFINITION_SCHEMA + 1));
  });

  it('写得不对、不是对象的报形状错', () => {
    assert.throws(() => upgradeDefinition(definition('one')), /must be a positive integer/);
    assert.throws(() => upgradeDefinition([]), /must be an object/);
  });
});
