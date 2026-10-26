/**
 * 接入方按名字注册的扩展（0005 的处理人来源 `resolver`、处理结果的 `guards`、自动节点的 `action`）。同 Java 的 Extensions：
 * 同名的后注册的换掉先注册的；用到没注册的名字时报 `flow.definition-invalid`（发布时就会查出来）。
 */
import { FlowError, type ProcessInstance, type WorkItem } from '@zerodep-dev/flow-model';
import type { Actor } from './identity.js';

/** 处理人解析器：`{ "resolver": "deptLeader", "args": { … } }` 算出的人 */
export interface PeopleResolver {
  readonly name: string;
  resolve(context: PeopleResolverContext): readonly bigint[] | Promise<readonly bigint[]>;
}

/** 解析器能拿到的 */
export interface PeopleResolverContext {
  readonly instance: ProcessInstance;
  /** 定义里写的参数，其中的 `{ ref }` 已经换成值 */
  readonly args: Readonly<Record<string, unknown>>;
  /** 按引用的名字取值 */
  readonly read: (name: string) => unknown;
}

/** 提交前的守卫：返回不通过的原因，通过时返回 null */
export interface OutcomeGuard {
  readonly name: string;
  check(context: OutcomeGuardContext): string | null | Promise<string | null>;
}

/** 守卫能拿到的 */
export interface OutcomeGuardContext {
  readonly actor: Actor;
  readonly instance: ProcessInstance;
  readonly workItem: WorkItem;
  readonly outcome: string;
  /** 合并好的表单值 */
  readonly form: Readonly<Record<string, unknown>>;
}

/** 自动节点的动作：要能重复做（用 idempotencyKey 去重） */
export interface ServiceAction {
  readonly name: string;
  perform(context: ServiceActionContext): ServiceActionResult | Promise<ServiceActionResult>;
}

/** 动作能拿到的 */
export interface ServiceActionContext {
  readonly instance: ProcessInstance;
  readonly node: string;
  readonly args: Readonly<Record<string, unknown>>;
  /** 去重用：同一个节点的同一轮相同（重试时也相同） */
  readonly idempotencyKey: string;
  readonly read: (name: string) => unknown;
}

/** 做完的结果：走哪个结果的出边（不给时走没写结果的那条），改哪些流程变量 */
export interface ServiceActionResult {
  readonly outcome?: string | null;
  readonly variables?: Readonly<Record<string, unknown>>;
}

/** 注册了的扩展 */
export class Extensions {
  readonly #resolvers = new Map<string, PeopleResolver>();
  readonly #guards = new Map<string, OutcomeGuard>();
  readonly #actions = new Map<string, ServiceAction>();

  constructor(resolvers: readonly PeopleResolver[] = [], guards: readonly OutcomeGuard[] = [], actions: readonly ServiceAction[] = []) {
    for (const r of resolvers) this.#resolvers.set(r.name, r);
    for (const g of guards) this.#guards.set(g.name, g);
    for (const a of actions) this.#actions.set(a.name, a);
  }

  resolver(name: string): PeopleResolver {
    return this.#resolvers.get(name) ?? unknown('resolver', name);
  }

  guard(name: string): OutcomeGuard {
    return this.#guards.get(name) ?? unknown('guard', name);
  }

  action(name: string): ServiceAction {
    return this.#actions.get(name) ?? unknown('action', name);
  }

  /** 发布时查名字：这一种里有没有注册 */
  isRegistered(kind: 'resolver' | 'guard' | 'action', name: string): boolean {
    const registry = kind === 'resolver' ? this.#resolvers : kind === 'guard' ? this.#guards : this.#actions;
    return registry.has(name);
  }
}

function unknown(kind: string, name: string): never {
  throw new FlowError('flow.definition-invalid', { message: `no ${kind} named ${name} is registered`, params: { kind, name } });
}
