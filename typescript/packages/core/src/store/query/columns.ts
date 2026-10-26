/**
 * 表的列与引擎里的类型之间的转换（同 Java 的 Columns）：有了它们，读出来的行直接就是引擎的记录。
 *   - utc：时刻——引擎里是带 Z 的 ISO 文字，库里是 UTC 的 DATETIME(3)；
 *   - json：JSON 列——引擎里是对象，库里是 JSON 文字（mysql2 读 JSON 列时已经解析好，两种都认）。
 */
import { CalendarDateTime } from '@internationalized/date';
import { converted, timestamp, type SqlType } from '@zerodep-dev/query-core';
import { RunState, type RunStateJson } from '../../run-state.js';
import { instantOf, type Instant } from '../../time.js';

/** 时刻 ↔ UTC 的本地时间 */
export const utc = converted(
  timestamp,
  {
    toDatabase: (at: Instant) => {
      const d = new Date(at);
      return new CalendarDateTime(
        d.getUTCFullYear(),
        d.getUTCMonth() + 1,
        d.getUTCDate(),
        d.getUTCHours(),
        d.getUTCMinutes(),
        d.getUTCSeconds(),
        d.getUTCMilliseconds(),
      );
    },
    fromDatabase: (v: CalendarDateTime) => instantOf(new Date(Date.UTC(v.year, v.month - 1, v.day, v.hour, v.minute, v.second, v.millisecond))),
  },
  'utc instant',
);

/** JSON 文字的一列：写时 JSON.stringify，读时已经是对象（mysql2）或文字（别的驱动）都认 */
function json<T>(name: string, read: (value: unknown) => T, write: (value: T) => unknown = (v) => v): SqlType<T, 'text'> {
  return Object.freeze({
    name,
    kind: 'text' as const,
    encode: (value: T) => JSON.stringify(write(value)),
    decode: (raw: unknown) => read(typeof raw === 'string' ? JSON.parse(raw) : raw),
  });
}

/**
 * 对象：表单的值、流程变量、摘要列、作业与流转记录的数据。摘要列、附加数据空的时候存 NULL（同 Java 的 ObjectJson），
 * 写的地方把空对象换成 null，读出 NULL 时存储里补成空对象。
 */
export const objectJson = json<Record<string, unknown>>('json object', (v) => (v ?? {}) as Record<string, unknown>);

/** 运行状态 */
export const runStateJson = json<RunState>(
  'run state',
  (v) => RunState.fromJson(v as RunStateJson),
  (state) => state.toJson(),
);

/** 正在处理的节点：[{ node, name }] */
export const currentNodesJson = json<{ node: string; name: string | null }[]>(
  'current nodes',
  (v) => ((v ?? []) as { node: string; name: string | null }[]).map((c) => ({ node: c.node, name: c.name ?? null })),
  (nodes) => nodes.map((c) => ({ node: c.node, name: c.name ?? c.node })),
);
