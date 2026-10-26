/**
 * 发布流程定义与表单（0006 的 3.5，同 Java 的 Publishing）：先按模型包的规则校验，有错误时不发布，把问题列表放进错误明细；
 * 通过了交给存储定版本号。引用的表单没写版本时定在当前最新的版本上；引用停用的表单报 form-disabled。
 */
import {
  FlowError,
  validateManifest,
  validateProcess,
  type FormManifest,
  type ProcessDefinition,
  type Problem,
  type ValidationContext,
} from '@zerodep-dev/flow-model';
import type { FormPublication } from '../command/types.js';
import { actorName, type Actor } from '../identity.js';
import { STATUS, type StoredDefinition, type StoredForm } from '../store/types.js';
import { instantOf } from '../time.js';
import type { DefinitionProblem, FormSummary, ProcessSummary } from '../view/types.js';
import { inTransaction, type EngineContext } from './context.js';
import { notFound } from './errors.js';

/** 发布一个流程定义（0005 的 JSON 文字）；校验有错误时报 `flow.definition-invalid`，警告不拦 */
export function publishDefinition(context: EngineContext, actor: Actor, json: string, note: string | null): Promise<StoredDefinition> {
  return inTransaction(context, async () => {
    const model = parseDefinition(json);
    const disabled = await disabledForm(context, model);
    if (disabled) {
      throw new FlowError('flow.definition-invalid', {
        message: `the form ${model.form!.key} is disabled`,
        errors: [{ code: disabled.code, field: 'form', params: disabled.params }],
      });
    }
    const form = model.form ? await formOf(context, model.form) : null;
    const manifest = form ? (JSON.parse(form.manifest) as FormManifest) : undefined;
    const errors = validateProcess(model, publishContext(context, manifest)).filter((p) => p.severity === 'error');
    if (errors.length > 0) {
      throw new FlowError('flow.definition-invalid', { message: `the definition has ${errors.length} error(s)`, errors: errors.map(detailOf) });
    }
    return context.stores.definitions.publish({
      processKey: model.key,
      name: model.name,
      category: model.category ?? null,
      model: json,
      formKey: form?.formKey ?? null,
      formVersion: form?.version ?? null,
      note,
      publishedBy: actor.id,
      publishedByName: actorName(actor),
      at: instantOf(context.clock.now()),
    });
  });
}

/** 校验一份定义，不发布：错误与警告都列出来。JSON 写错、形状不对时只有一条 malformed */
export async function validateDefinition(context: EngineContext, json: string): Promise<DefinitionProblem[]> {
  let model: ProcessDefinition;
  try {
    model = parseDefinition(json);
  } catch (error) {
    return [problem('malformed', 'error', { reason: (error as FlowError).params['reason'] ?? String(error) })];
  }
  let manifest: FormManifest | undefined;
  if (model.form) {
    const form = model.form.version == null ? await context.stores.forms.latest(model.form.key) : await context.stores.forms.version(model.form.key, model.form.version);
    if (!form) return [{ ...problem('form-unknown', 'error', { key: model.form.key }), field: 'form' }];
    manifest = JSON.parse(form.manifest) as FormManifest;
  }
  const out: DefinitionProblem[] = [];
  const disabled = await disabledForm(context, model);
  if (disabled) out.push(disabled);
  for (const p of validateProcess(model, publishContext(context, manifest))) {
    out.push({ code: p.code, severity: p.severity, node: p.node ?? null, edge: p.edge ?? null, lane: p.lane ?? null, field: p.field ?? null, params: texts(p.params) });
  }
  return out;
}

/** 停用、启用流程：停用后不能再发起，进行中的照常走完 */
export async function setProcessStatus(context: EngineContext, processKey: string, active: boolean): Promise<ProcessSummary> {
  if (!(await context.stores.definitions.setStatus(processKey, active ? STATUS.ACTIVE : STATUS.DISABLED))) throw notFound('process', processKey);
  const p = (await context.stores.definitions.process(processKey))!;
  return { key: p.processKey, name: p.name, category: p.category, status: p.status, latestVersion: p.latestVersion };
}

/** 停用、启用表单：停用后新发布的流程不能再选它，已经引用它的流程照常用 */
export async function setFormStatus(context: EngineContext, formKey: string, active: boolean): Promise<FormSummary> {
  if (!(await context.stores.forms.setStatus(formKey, active ? STATUS.ACTIVE : STATUS.DISABLED))) throw notFound('form', formKey);
  const f = (await context.stores.forms.form(formKey))!;
  return { key: f.formKey, name: f.name, category: f.category, status: f.status, latestVersion: f.latestVersion };
}

/** 发布一张表单：先校验字段清单本身（0004 的 1.2），有错误时报 `flow.validation-failed` */
export function publishForm(context: EngineContext, actor: Actor, form: FormPublication): Promise<StoredForm> {
  return inTransaction(context, async () => {
    let manifest: FormManifest;
    try {
      manifest = JSON.parse(form.manifest) as FormManifest;
    } catch (error) {
      throw new FlowError('flow.validation-failed', { message: String(error), errors: [{ code: 'malformed', field: 'manifest', params: { reason: String(error) } }] });
    }
    const errors = validateManifest(manifest).filter((p) => p.severity === 'error');
    if (errors.length > 0) {
      throw new FlowError('flow.validation-failed', { message: `the form manifest has ${errors.length} error(s)`, errors: errors.map(detailOf) });
    }
    return context.stores.forms.publish({
      formKey: form.key,
      name: form.name,
      category: form.category ?? null,
      manifest: form.manifest,
      clientSource: form.clientSource ?? null,
      clientCompiled: form.clientCompiled ?? null,
      compilerVersion: form.compilerVersion ?? null,
      serverLanguage: form.serverLanguage ?? null,
      serverSource: form.serverSource ?? null,
      note: form.note ?? null,
      publishedBy: actor.id,
      publishedByName: actorName(actor),
      at: instantOf(context.clock.now()),
    });
  });
}

/**
 * 读定义的 JSON；形状不对（缺键、类型不对、JSON 写错）时报 `flow.definition-invalid`（malformed，reason 里是出错的位置）。
 * 只查引擎推进离不开的形状，其余交给 validateProcess。
 */
export function parseDefinition(json: string): ProcessDefinition {
  let model: unknown;
  try {
    model = JSON.parse(json);
  } catch (error) {
    throw malformed((error as Error).message);
  }
  const object = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null && !Array.isArray(value);
  if (!object(model)) throw malformed('the definition must be an object');
  if (typeof model['key'] !== 'string' || typeof model['name'] !== 'string') throw malformed('key and name must be text');
  if (!Array.isArray(model['nodes']) || !Array.isArray(model['edges'])) throw malformed('nodes and edges must be lists');
  (model['nodes'] as unknown[]).forEach((node, i) => {
    if (!object(node) || typeof node['id'] !== 'string' || typeof node['type'] !== 'string') throw malformed(`nodes[${i}] needs id and type`);
  });
  (model['edges'] as unknown[]).forEach((edge, i) => {
    if (!object(edge) || typeof edge['id'] !== 'string' || typeof edge['from'] !== 'string' || typeof edge['to'] !== 'string') {
      throw malformed(`edges[${i}] needs id, from and to`);
    }
  });
  return model as unknown as ProcessDefinition;
}

const malformed = (reason: string) => new FlowError('flow.definition-invalid', { message: reason, errors: [{ code: 'malformed', params: { reason } }], params: { reason } });

/** 定义引用的表单：写了版本取那个版本，没写取最新的 */
async function formOf(context: EngineContext, ref: { key: string; version?: number }): Promise<StoredForm> {
  const form = ref.version == null ? await context.stores.forms.latest(ref.key) : await context.stores.forms.version(ref.key, ref.version);
  if (!form) throw notFound('form', ref.version == null ? ref.key : `${ref.key}@${ref.version}`);
  return form;
}

/** 引用的表单停用了（0004 的 5.3）：form-disabled；没停用、没引用表单时为 null */
async function disabledForm(context: EngineContext, model: ProcessDefinition): Promise<DefinitionProblem | null> {
  if (!model.form) return null;
  const form = await context.stores.forms.form(model.form.key);
  return form?.status === STATUS.DISABLED ? { ...problem('form-disabled', 'error', { key: model.form.key }), field: 'form' } : null;
}

/** 发布时的校验上下文：表单的字段清单（查 form. 引用）与注册了的解析器、动作、守卫；自定义节点类型不知道，不查 */
function publishContext(context: EngineContext, form: FormManifest | undefined): ValidationContext {
  return {
    ...(form ? { form } : {}),
    isRegistered: (kind, name) => {
      if (kind === 'node') return true;
      if (kind === 'resolver' || kind === 'guard' || kind === 'action') return context.extensions.isRegistered(kind, name);
      return false;
    },
  };
}

function problem(code: string, severity: 'error' | 'warning', params: Record<string, string>): DefinitionProblem {
  return { code, severity, node: null, edge: null, lane: null, field: null, params };
}

/** 问题写成错误明细 */
function detailOf(p: Problem) {
  return {
    code: p.code,
    ...(p.field !== undefined ? { field: p.field } : {}),
    ...(p.node !== undefined ? { node: p.node } : {}),
    ...(p.edge !== undefined ? { edge: p.edge } : {}),
    ...(p.lane !== undefined ? { lane: p.lane } : {}),
    ...(p.params ? { params: texts(p.params) } : {}),
  };
}

const texts = (params: Readonly<Record<string, string | number>> | undefined): Record<string, string> =>
  Object.fromEntries(Object.entries(params ?? {}).map(([k, v]) => [k, String(v)]));
