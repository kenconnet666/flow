/**
 * 时限算成时刻（0005 的 5.3 与定时节点，同 Java 的 Deadlines）：`{ "hours": 4 }` 这样的一段时长从某一刻往后加，工作日按日历跳过休息日；
 * `{ "until": … }` 是一个时刻或日期（可以是字段引用），只写日期时算那天结束（第二天零点）。
 */
import { isRef, type Due, type Duration } from '@zerodep-dev/flow-model';
import { zonedInstant, type WorkCalendar } from '../calendar.js';
import { instantOf, plusSeconds, type Instant } from '../time.js';

/** 时长的单位与数量 */
function partsOf(duration: Duration): { unit: 'workdays' | 'days' | 'hours' | 'minutes'; amount: number } {
  if ('workdays' in duration) return { unit: 'workdays', amount: duration.workdays };
  if ('days' in duration) return { unit: 'days', amount: duration.days };
  if ('hours' in duration) return { unit: 'hours', amount: duration.hours };
  return { unit: 'minutes', amount: duration.minutes };
}

const SECONDS = { days: 86_400, hours: 3_600, minutes: 60 } as const;

/** 时限的换算 */
export class Deadlines {
  /** @param calendarName 流程定义里写的日历名字（settings.calendar）；没写时为 null */
  constructor(
    private readonly calendar: WorkCalendar,
    private readonly calendarName: string | null,
  ) {}

  /** 时限到期的时刻；until 的引用没有值、不是时间时为 null（没有时限） */
  of(due: Due, from: Instant, read: (name: string) => unknown): Instant | null {
    if (!('until' in due)) return this.plus(due, from);
    const value = isRef(due.until) ? read(due.until.ref) : due.until;
    return value == null ? null : this.#moment(String(value));
  }

  /** from 之后一段时长 */
  plus(duration: Duration, from: Instant): Instant {
    const { unit, amount } = partsOf(duration);
    if (unit === 'workdays') return this.calendar.plusWorkdays(from, amount, this.calendarName);
    return plusSeconds(from, Math.trunc(amount * SECONDS[unit]));
  }

  /** to 之前一段时长（到期前的提醒） */
  minus(duration: Duration, to: Instant): Instant {
    const { unit, amount } = partsOf(duration);
    return this.plus({ [unit]: -amount } as Duration, to);
  }

  /** 时刻（带 Z 或时区）、本地时间（按日历的时区）、日期（那天结束）；都不是时为 null */
  #moment(text: string): Instant | null {
    if (/[zZ]$|[+-]\d{2}:\d{2}$/.test(text) && !Number.isNaN(Date.parse(text))) return instantOf(new Date(text));
    if (/^\d{4}-\d{2}-\d{2}$/.test(text)) {
      // 那天结束 = 第二天在日历时区的零点（夏令时那天不一定是 24 小时）
      const [y, m, d] = text.split('-').map(Number);
      const next = new Date(Date.UTC(y!, m! - 1, d! + 1)).toISOString().slice(0, 10);
      return zonedInstant(next, this.calendar.zone);
    }
    return zonedInstant(text, this.calendar.zone);
  }
}
