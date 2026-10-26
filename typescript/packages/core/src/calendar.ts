/**
 * 工作日历（0005 第 2 节的 `calendar`）：时限写成 `{ "workdays": 2 }` 时按它算。默认 {@link weekdays} 只跳过周六周日；
 * 有节假日、调休的接入方实现这个接口。时间都按 UTC 存，"哪一天"按日历的时区算。同 Java 的 WorkCalendar。
 */
import { instantOf, millisOf, type Instant } from './time.js';

/** 工作日历 */
export interface WorkCalendar {
  /** 时区（IANA 名字，如 `Asia/Shanghai`）：只写日期的时限按它取那天的结束 */
  readonly zone: string;
  /** 从 from 起往后数 days 个工作日（同一时刻），负数往前数；小数部分按 24 小时折算 */
  plusWorkdays(from: Instant, days: number, calendar: string | null): Instant;
}

/** 周一到周五上班，没有节假日：在 zone 的日期上跳过周六周日 */
export function weekdays(zone = 'UTC'): WorkCalendar {
  return {
    zone,
    plusWorkdays(from, days, _calendar) {
      const whole = Math.trunc(days);
      const step = Math.sign(whole);
      let at = millisOf(from);
      for (let left = Math.abs(whole); left > 0; ) {
        at += step * 86_400_000;
        const weekday = new Intl.DateTimeFormat('en-US', { timeZone: zone, weekday: 'short' }).format(new Date(at));
        if (weekday !== 'Sat' && weekday !== 'Sun') left--;
      }
      return instantOf(new Date(at + Math.trunc((days - whole) * 86_400) * 1000));
    },
  };
}

/** zone 里的本地时间（`2026-10-26T18:00` 或带秒）对应的时刻 */
export function zonedInstant(local: string, zone: string): Instant | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})(?:[T ](\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,3}))?)?)?$/.exec(local);
  if (!m) return null;
  const [y, mo, d, h = '0', mi = '0', s = '0', ms = '0'] = m.slice(1);
  const asUtc = Date.UTC(+y!, +mo! - 1, +d!, +h, +mi, +s, +ms.padEnd(3, '0'));
  // 先当作 UTC，再减去这一刻在 zone 里的偏移（夏令时边界上偏一小时的情形按第二次的偏移修正）
  const first = asUtc - offsetOf(asUtc, zone);
  return instantOf(new Date(asUtc - offsetOf(first, zone)));
}

/** 这一刻在 zone 里比 UTC 快多少毫秒 */
function offsetOf(millis: number, zone: string): number {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: zone,
    hourCycle: 'h23',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).formatToParts(new Date(millis));
  const get = (type: string) => Number(parts.find((p) => p.type === type)?.value);
  const local = Date.UTC(get('year'), get('month') - 1, get('day'), get('hour'), get('minute'), get('second'));
  return local - Math.floor(millis / 1000) * 1000;
}
