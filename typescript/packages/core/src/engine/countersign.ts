/**
 * 会签的一个人处理完之后，节点怎样往下（0005 的 5.2，同 Java 的 Countersign）：
 * <pre>
 *   complete     何时结束                                          按哪个结果结束
 *   all          有人选了非同意的结果、或所有人都处理完              非同意的那个；都同意时按同意
 *   sequential   同上，一个接一个                                  同上
 *   any          第一个人处理完                                    他选的
 *   ratio        选同一结果的人数占比达到 ratio                     先达到的那个；都处理完还没有就按 fallback
 *   votes        选同一结果的票数（权重之和）达到 votes             同上
 * </pre>
 * veto（一票否决）只对 ratio、votes 有意义：有人选了它，节点立即按它结束。只看这个节点这一轮的待办。
 */
import type { MultiInstance, Outcome, WorkItem } from '@zerodep-dev/flow-model';

/** 处理完一个人之后的决定：还要等别人、轮到下一个人（依次会签）、节点按这个结果结束 */
export type Decision = { readonly kind: 'wait' } | { readonly kind: 'next' } | { readonly kind: 'finish'; readonly outcome: string };

const WAIT: Decision = { kind: 'wait' };
const NEXT: Decision = { kind: 'next' };
const finish = (outcome: string): Decision => ({ kind: 'finish', outcome });

/**
 * 决定节点怎样往下。
 *
 * @param chosen  刚处理完的人选的结果
 * @param items   这个节点这一轮的全部待办（含刚处理完的那个）
 * @param waiting 依次会签还没轮到的人数
 */
export function decide(multi: MultiInstance, chosen: Outcome, items: readonly WorkItem[], waiting: number): Decision {
  const approve = (chosen.kind ?? 'approve') === 'approve';
  const someoneOpen = items.some((i) => i.state === 'open' || i.state === 'waiting') || waiting > 0;
  switch (multi.complete) {
    case 'any':
      return finish(chosen.key);
    case 'all':
      return !approve || !someoneOpen ? finish(chosen.key) : WAIT;
    case 'sequential':
      return !approve ? finish(chosen.key) : waiting > 0 ? NEXT : finish(chosen.key);
    case 'ratio':
    case 'votes':
      return threshold(multi, chosen, items, someoneOpen);
    default:
      throw new Error(`countersign rule '${multi.complete}' is not supported`);
  }
}

/** 票签：一票否决立即结束；这个结果达到门槛就按它结束；都处理完还没有达到的就按 fallback */
function threshold(multi: MultiInstance, chosen: Outcome, items: readonly WorkItem[], someoneOpen: boolean): Decision {
  if (chosen.veto) return finish(chosen.key);
  const same = items.filter((i) => i.outcome === chosen.key);
  // 比例按 6 位小数四舍五入后比（同 Java 的 BigDecimal 算法）
  const reached =
    multi.complete === 'ratio'
      ? Math.round((same.length / items.length) * 1e6) / 1e6 >= (multi.ratio ?? 1)
      : same.reduce((sum, i) => sum + (i.weight ?? 1), 0) >= (multi.votes ?? Number.POSITIVE_INFINITY);
  if (reached) return finish(chosen.key);
  return someoneOpen ? WAIT : finish(multi.fallback!);
}
