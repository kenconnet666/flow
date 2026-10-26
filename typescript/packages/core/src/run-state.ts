/**
 * 实例的运行状态（0007 的 `flow_instance.state`，引擎内部用，不给前端）。不可变：推进时换出新的一份。同 Java 的 `RunState`，
 * 存成同一种 JSON（`{ rounds, waiting, returns, queues, waits }`），两个后端读写同一张表时互通。
 */
import type { Id } from '@zerodep-dev/flow-model';
import type { Instant } from './time.js';

/** 存进库里的样子 */
export interface RunStateJson {
  readonly rounds?: Readonly<Record<string, number>>;
  readonly waiting?: Readonly<Record<string, number>>;
  readonly returns?: Readonly<Record<string, string>>;
  readonly queues?: Readonly<Record<string, readonly (number | string)[]>>;
  readonly waits?: Readonly<Record<string, string>>;
}

/** 实例的运行状态 */
export class RunState {
  /** 刚发起 */
  static readonly INITIAL = new RunState({}, {}, {}, {}, {});

  /**
   * @param rounds  节点 → 到过几轮（`sys.round.节点`、待办的轮次）
   * @param waiting 并行、包容的汇合 → 还有几条分支没到
   * @param returns 被退回的节点 → 退回它的节点（`returnTo: "resubmit"`）
   * @param queues  依次会签的节点 → 还没轮到的人
   * @param waits   停着等的节点（定时、异步的自动节点、等事件、调用）→ 等到什么时候
   */
  private constructor(
    readonly rounds: Readonly<Record<string, number>>,
    readonly waiting: Readonly<Record<string, number>>,
    readonly returns: Readonly<Record<string, string>>,
    readonly queues: Readonly<Record<string, readonly Id[]>>,
    readonly waits: Readonly<Record<string, Instant>>,
  ) {}

  /** 从库里的 JSON 读 */
  static fromJson(json: RunStateJson | null | undefined): RunState {
    if (!json) return RunState.INITIAL;
    const queues: Record<string, Id[]> = {};
    for (const [node, ids] of Object.entries(json.queues ?? {})) queues[node] = ids.map((id) => BigInt(id));
    return new RunState({ ...json.rounds }, { ...json.waiting }, { ...json.returns }, queues, { ...json.waits });
  }

  /** 写成库里的 JSON（用户编号写成数，同 Java） */
  toJson(): RunStateJson {
    const queues: Record<string, number[]> = {};
    for (const [node, ids] of Object.entries(this.queues)) queues[node] = ids.map(Number);
    return { rounds: this.rounds, waiting: this.waiting, returns: this.returns, queues, waits: this.waits };
  }

  // ───────── 轮次 ─────────

  /** 节点到过几轮；没到过是 0 */
  round(node: string): number {
    return this.rounds[node] ?? 0;
  }

  /** 再到一次这个节点 */
  enter(node: string): RunState {
    return this.with({ rounds: { ...this.rounds, [node]: this.round(node) + 1 } });
  }

  // ───────── 并行、包容的汇合 ─────────

  /** 分叉走了 branches 条：汇合 join 要等这么多条 */
  fork(join: string, branches: number): RunState {
    return this.with({ waiting: { ...this.waiting, [join]: branches } });
  }

  /** 汇合 join 还要等几条；没有在等时是 0 */
  waitingAt(join: string): number {
    return this.waiting[join] ?? 0;
  }

  /** 一条分支到了汇合 join：少等一条，等齐了就不再记它 */
  arrive(join: string): RunState {
    const remaining = (this.waiting[join] ?? 1) - 1;
    const next = { ...this.waiting };
    if (remaining > 0) next[join] = remaining;
    else delete next[join];
    return this.with({ waiting: next });
  }

  /** 不再等汇合 join（区域里的分支都取消了：退回跳出了区域） */
  forget(join: string | null | undefined): RunState {
    if (join == null || !(join in this.waiting)) return this;
    const next = { ...this.waiting };
    delete next[join];
    return this.with({ waiting: next });
  }

  // ───────── 退回后直接回去 ─────────

  /** target 被 from 退回了，重新提交后直接回到 from */
  returned(target: string, from: string): RunState {
    return this.with({ returns: { ...this.returns, [target]: from } });
  }

  /** node 重新提交后要直接回去的节点；没有时为 null */
  resubmitTo(node: string): string | null {
    return this.returns[node] ?? null;
  }

  /** node 重新提交了，不再记它 */
  resubmitted(node: string): RunState {
    if (!(node in this.returns)) return this;
    const next = { ...this.returns };
    delete next[node];
    return this.with({ returns: next });
  }

  // ───────── 跳转 ─────────

  /** 管理员跳转：不再等汇合、定时与异步的节点，不再记退回后直接回去与依次会签的队列；轮次照旧 */
  withoutPending(): RunState {
    return new RunState(this.rounds, {}, {}, {}, {});
  }

  // ───────── 依次会签 ─────────

  /** 依次会签的节点还没轮到的人；没有时是空列表 */
  queue(node: string): readonly Id[] {
    return this.queues[node] ?? [];
  }

  /** 换掉依次会签的节点还没轮到的人；空列表就是不再记它 */
  withQueue(node: string, remaining: readonly Id[]): RunState {
    const next = { ...this.queues };
    if (remaining.length === 0) delete next[node];
    else next[node] = [...remaining];
    return this.with({ queues: next });
  }

  // ───────── 停着等的节点 ─────────

  /** 停在 node 上等：定时节点等到 until，异步的自动节点等作业做完（until 是排作业的时刻） */
  waitUntil(node: string, until: Instant): RunState {
    return this.with({ waits: { ...this.waits, [node]: until } });
  }

  /** node 不再等了：到时候了、作业做完了，或它所在的分支取消了 */
  waitDone(node: string): RunState {
    if (!(node in this.waits)) return this;
    const next = { ...this.waits };
    delete next[node];
    return this.with({ waits: next });
  }

  private with(changes: Partial<Pick<RunState, 'rounds' | 'waiting' | 'returns' | 'queues' | 'waits'>>): RunState {
    return new RunState(
      changes.rounds ?? this.rounds,
      changes.waiting ?? this.waiting,
      changes.returns ?? this.returns,
      changes.queues ?? this.queues,
      changes.waits ?? this.waits,
    );
  }
}
