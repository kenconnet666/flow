/**
 * 管理端运维用的：实例搜索与迁移、改数据、跳转，统计，工作日历，异常（失败的作业）。
 */
import type { CalendarDate } from '@internationalized/date';
import type { Id, Moment } from '@zerodep-dev/flow-model';

/** 管理员把实例迁到流程的另一个版本 */
export interface MigrateCommand {
  readonly instanceId: Id;
  readonly version: number;
  /** 旧节点 → 新节点；同名的可以不写 */
  readonly nodes: Readonly<Record<string, string>>;
  readonly reason: string;
}

/** 统计：一个流程的实例数 */
export interface ProcessStats {
  readonly processKey: string;
  readonly running: number;
  readonly completed: number;
  readonly stopped: number;
}

/** 统计：一个人工节点的处理情况 */
export interface NodeStats {
  readonly node: string;
  readonly nodeName: string | null;
  readonly done: number;
  readonly averageHours: number;
  readonly overdueRate: number;
}

/** 统计：一个处理人的工作量 */
export interface HandlerStats {
  readonly userId: Id;
  readonly userName: string | null;
  readonly done: number;
  readonly open: number;
}

/** 一份工作日历 */
export interface WorkCalendar {
  readonly name: string;
  readonly zone: string;
  /** 一周哪几天上班：1 是周一，7 是周日，逗号分隔 */
  readonly workweek: string;
  readonly days: readonly WorkCalendarDay[];
}

/** 工作日历的一个特殊日子：节假日不上班，调休补班要上班 */
export interface WorkCalendarDay {
  readonly day: CalendarDate;
  readonly workday: boolean;
  readonly note: string | null;
}

/** 管理员改数据 */
export interface ChangeDataCommand {
  readonly instanceId: Id;
  readonly variables?: Readonly<Record<string, unknown>>;
  readonly form?: Readonly<Record<string, unknown>>;
  readonly reason: string;
}

/** 管理端搜实例的条件：不写的不限 */
export interface InstanceSearch {
  readonly status?: string;
  readonly processKey?: string;
  readonly keyword?: string;
}

/** 异常（管理端）：重试用完的作业，多半是自动节点调外部系统失败 */
export interface Incident {
  readonly jobId: Id;
  /** 作业的种类：action 自动节点的动作、timer、overdue…… */
  readonly kind: string;
  readonly instanceId: Id;
  readonly instanceTitle: string | null;
  readonly processKey: string | null;
  /** 卡在哪个节点 */
  readonly node: string | null;
  readonly nodeName: string | null;
  /** 试了几次 */
  readonly attempts: number;
  /** 最后一次失败的原因 */
  readonly error: string | null;
  /** 最后一次尝试的时间 */
  readonly failedAt: Moment;
  /** 能跳过、按结果往下走（自动节点的动作，实例还停在那里） */
  readonly skippable: boolean;
  /** 跳过时能选的结果；空的时不用选 */
  readonly outcomes: readonly string[];
}

/** 跳过失败的作业（管理员） */
export interface SkipJobCommand {
  readonly jobId: Id;
  /** 走哪个结果的出边；节点只有一条没写结果的出边时不填 */
  readonly outcome?: string;
  readonly reason?: string;
}

/** 管理员跳转：从指定的节点重新走 */
export interface JumpCommand {
  readonly instanceId: Id;
  readonly node: string;
  readonly reason?: string;
}
