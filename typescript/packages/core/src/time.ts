/**
 * 时刻：引擎里都是带 `Z` 的 ISO 8601 文字（同模型包的运行时类型、0001 的"UTC 时间"），写法同 Java 的 `Instant.toString()`——
 * 毫秒是 0 时不写小数部分（`2026-10-26T08:00:00Z`），两个后端写出来的一样，场景的期望才能共用。
 *
 *   const now = instantOf(clock.now());          // '2026-10-26T08:00:00Z'
 *   plusSeconds(now, 3600);                      // '2026-10-26T09:00:00Z'
 */

/** 时刻的文字 */
export type Instant = string;

/** Date 写成时刻的文字 */
export function instantOf(date: Date): Instant {
  const iso = date.toISOString();
  return iso.endsWith('.000Z') ? `${iso.slice(0, -5)}Z` : iso;
}

/** 时刻的毫秒数 */
export const millisOf = (at: Instant): number => Date.parse(at);

/** at 之后 seconds 秒（可以是负数） */
export const plusSeconds = (at: Instant, seconds: number): Instant => instantOf(new Date(millisOf(at) + seconds * 1000));

/** a 比 b 晚 */
export const isAfter = (a: Instant, b: Instant): boolean => millisOf(a) > millisOf(b);

/** 引擎的时钟：测试里用固定的、能往前拨的 */
export interface Clock {
  now(): Date;
}

/** 系统时钟 */
export const systemClock: Clock = { now: () => new Date() };
