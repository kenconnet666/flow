/**
 * 内置作业的种类与作业数据里的键（同 Java 的 BuiltinJobs 常量）：推进时排作业（Execution），到点由作业的处理做。
 * 作业的轮询与内置作业的处理随 TS 引擎的下一步补上；表与写法与 Java 相同，两个后端排的作业互通。
 */

/** 内置作业的种类 */
export const JOB = {
  /** 定时节点到点 */
  TIMER: 'timer',
  /** 待办超时 */
  OVERDUE: 'overdue',
  /** 提醒 */
  REMIND: 'remind',
  /** 进收件箱很久没人认领 */
  UNCLAIMED: 'unclaimed',
  /** 异步的自动节点做动作 */
  ACTION: 'action',
  /** 等待节点等超时 */
  RECEIVE_TIMEOUT: 'receiveTimeout',
} as const;

/** 内置作业最多试几次 */
export const MAX_ATTEMPTS = 3;
