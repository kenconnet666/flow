/**
 * 解析好的发布版本（同 Java 的 LoadedDefinition、DefinitionCache）：定义的图、网关配对，加上它用的表单版本与字段清单。
 * 发布的版本不会变，解析一次就按编号记住；集群里别的节点发布新版本时不用通知——新版本是新编号。
 */
import {
  gatewayPairs,
  processGraph,
  type FormManifest,
  type GatewayPairs,
  type Id,
  type ProcessDefinition,
  type ProcessGraph,
} from '@zerodep-dev/flow-model';
import type { InstanceRecord, StoredDefinition, StoredForm, Stores } from '../store/types.js';
import { notFound } from './errors.js';

/** 解析好的一个发布版本 */
export interface LoadedDefinition {
  readonly stored: StoredDefinition;
  readonly model: ProcessDefinition;
  readonly graph: ProcessGraph;
  /** 并行、包容网关的分叉与汇合配对 */
  readonly pairs: GatewayPairs;
  /** 用的表单版本；流程没有表单时为 null */
  readonly form: StoredForm | null;
  /** 表单的字段清单；流程没有表单时为 null */
  readonly manifest: FormManifest | null;
}

/** 发布的版本，解析一次就记住 */
export class DefinitionCache {
  readonly #loaded = new Map<Id, Promise<LoadedDefinition>>();

  constructor(private readonly stores: Stores) {}

  /** 实例按哪个版本走 */
  async ofInstance(instance: InstanceRecord): Promise<LoadedDefinition> {
    const found = this.#loaded.get(instance.definitionId);
    if (found) return found;
    const stored = await this.stores.definitions.byId(instance.definitionId);
    if (!stored) throw notFound('definition', instance.definitionId);
    return this.of(stored);
  }

  /** 某个存着的版本 */
  of(stored: StoredDefinition): Promise<LoadedDefinition> {
    let loaded = this.#loaded.get(stored.id);
    if (!loaded) {
      loaded = this.#load(stored);
      // 读坏了（表单不在了）时不记住，下次再读
      loaded.catch(() => this.#loaded.delete(stored.id));
      this.#loaded.set(stored.id, loaded);
    }
    return loaded;
  }

  async #load(stored: StoredDefinition): Promise<LoadedDefinition> {
    const model = JSON.parse(stored.model) as ProcessDefinition;
    const graph = processGraph(model);
    const pairs = gatewayPairs(model);
    if (stored.formKey == null) return { stored, model, graph, pairs, form: null, manifest: null };
    const form = await this.stores.forms.version(stored.formKey, stored.formVersion!);
    if (!form) throw notFound('form', `${stored.formKey}@${stored.formVersion}`);
    return { stored, model, graph, pairs, form, manifest: JSON.parse(form.manifest) as FormManifest };
  }
}
