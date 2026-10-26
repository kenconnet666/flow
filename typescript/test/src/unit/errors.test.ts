import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { FLOW_ERROR_CODES, FlowError, apiFail, apiOk } from '@zerodep-dev/flow-model';
import { readSpec } from './spec.js';

describe('业务错误码（spec/error-codes.json）', () => {
  it('常量与清单一致，先后也一样', () => {
    const spec = readSpec<{ codes: { code: string }[] }>('error-codes.json');
    assert.deepEqual([...FLOW_ERROR_CODES], spec.codes.map((c) => c.code));
  });

  it('响应：成功是 ok；带码的错误原样用码，别的包的码也原样用，其余是 internal-error', () => {
    assert.deepEqual(apiOk(1), { code: 'ok', data: 1 });
    assert.deepEqual(apiFail(new FlowError('flow.work-item-not-open', { params: { workItem: '7' } })), {
      code: 'flow.work-item-not-open',
      message: 'flow.work-item-not-open',
      params: { workItem: '7' },
    });
    const queryError = Object.assign(new Error('rows'), { code: 'query.unexpected-row-count' });
    assert.equal(apiFail(queryError).code, 'query.unexpected-row-count');
    assert.equal(apiFail(new TypeError('x')).code, 'flow.internal-error');
  });
});
