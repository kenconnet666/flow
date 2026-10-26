/**
 * 引擎内部各处理类共用的东西（同 Java 的 EngineContext、EventDispatcher），组装一次：
 *   - transaction：命令在引擎的事务里做（外面已有同一个库的事务时加入）；最外层做完（提交了）发提交后的监听，回滚了就丢掉；
 *   - published：写下的流转记录交给监听，事务内的马上调（抛错整个命令回滚），提交后的先记下。
 * 嵌套的事务（作业里调处理类）用 AsyncLocalStorage 记层数，只在最外层做完时发。
 */
import { AsyncLocalStorage } from 'node:async_hooks';
import type { FlowEvent, ProcessGraph, ProcessInstance } from '@zerodep-dev/flow-model';
import type { Database } from '@zerodep-dev/query-core';
import type { WorkCalendar } from '../calendar.js';
import type { Extensions } from '../extensions.js';
import type { OrgDirectory } from '../identity.js';
import type { Stores } from '../store/types.js';
import type { Clock } from '../time.js';
import type { DefinitionCache } from './definitions.js';

/** 给监听的一条通知：流转记录、它所在的实例（写记录那一刻的样子），以及节点上的业务附加信息（meta） */
export interface FlowNotice {
  readonly event: FlowEvent;
  readonly instance: ProcessInstance;
  readonly nodeMeta: Readonly<Record<string, unknown>>;
}

/**
 * 流转的监听（0006 第 5 节）：每条流转记录写下时都会给它看一眼。发通知、同步业务表、审计。
 *   - afterCommit（默认）：命令的事务提交之后调；抛错只记日志，不影响流转；
 *   - inTransaction：写流转记录的那一刻在同一个事务里调；抛错整个命令回滚。
 */
export interface FlowListener {
  on(notice: FlowNotice): void | Promise<void>;
  readonly phase?: 'afterCommit' | 'inTransaction';
  /** 只听这几种事件；不给时全部 */
  readonly types?: ReadonlySet<string>;
  /** 只听这几个流程；不给时全部 */
  readonly processes?: ReadonlySet<string>;
}

interface Pending {
  depth: number;
  readonly notices: FlowNotice[];
}

/** 把写下的流转记录交给监听，管着引擎的事务边界 */
export class EventDispatcher {
  readonly #inTransaction: FlowListener[];
  readonly #afterCommit: FlowListener[];
  readonly #pending = new AsyncLocalStorage<Pending>();

  constructor(listeners: readonly FlowListener[]) {
    this.#inTransaction = listeners.filter((l) => l.phase === 'inTransaction');
    this.#afterCommit = listeners.filter((l) => (l.phase ?? 'afterCommit') === 'afterCommit');
  }

  /** 在引擎的事务里做；最外层做完（提交了）发提交后的通知，出错时丢掉 */
  async transaction<T>(db: Database, work: () => Promise<T>): Promise<T> {
    const outer = this.#pending.getStore();
    if (outer) return db.transaction(() => work());
    const pending: Pending = { depth: 1, notices: [] };
    const result = await this.#pending.run(pending, () => db.transaction(() => work()));
    await this.#deliver(pending.notices);
    return result;
  }

  /** 写下了这些流转记录（带编号）：事务内的监听马上调，提交后的记下 */
  async published(events: readonly FlowEvent[], instance: ProcessInstance, graph: ProcessGraph): Promise<void> {
    if (this.#inTransaction.length === 0 && this.#afterCommit.length === 0) return;
    for (const event of events) {
      const notice: FlowNotice = { event, instance, nodeMeta: (event.node ? graph.node(event.node)?.meta : undefined) ?? {} };
      for (const listener of this.#inTransaction) if (accepts(listener, notice)) await listener.on(notice);
      if (this.#afterCommit.some((l) => accepts(l, notice))) this.#pending.getStore()?.notices.push(notice);
    }
  }

  async #deliver(notices: readonly FlowNotice[]) {
    for (const notice of notices) {
      for (const listener of this.#afterCommit) {
        if (!accepts(listener, notice)) continue;
        try {
          await listener.on(notice);
        } catch (error) {
          console.error(`flow listener failed on ${notice.event.type} of instance ${notice.event.instanceId}`, error);
        }
      }
    }
  }
}

function accepts(listener: FlowListener, notice: FlowNotice): boolean {
  return (!listener.types || listener.types.has(notice.event.type)) && (!listener.processes || listener.processes.has(notice.instance.processKey));
}

/** 引擎内部共用的东西 */
export interface EngineContext {
  readonly db: Database;
  readonly stores: Stores;
  readonly clock: Clock;
  readonly org: OrgDirectory;
  readonly calendar: WorkCalendar;
  readonly dispatcher: EventDispatcher;
  readonly extensions: Extensions;
  readonly definitions: DefinitionCache;
}

/** 在引擎的事务里做 */
export const inTransaction = <T>(context: EngineContext, work: () => Promise<T>): Promise<T> => context.dispatcher.transaction(context.db, work);
