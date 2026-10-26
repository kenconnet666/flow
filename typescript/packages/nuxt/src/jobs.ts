/**
 * 定时做到期的作业（定时节点、超时、提醒、无人认领），同 Java flow-spring 的 FlowJobPoller：每隔 intervalMs 调一次
 * `engine.runDueJobs()`，一批做满了接着做，直到没有到期的；多个进程同时跑不会重复做（引擎抢占作业）。
 * 顺带每小时删一次过期的幂等记录（`engine.purgeRequests`，留 retentionSeconds）。
 *
 * Nitro 里放在插件中，进程退出时停掉：
 *
 *   export default defineNitroPlugin((nitro) => {
 *     const jobs = startFlowJobs(engine);
 *     nitro.hooks.hook('close', jobs.stop);
 *   });
 *
 * 交给已有的调度平台时不调它，由平台定时调 `engine.runDueJobs()` 与 `engine.purgeRequests(...)`。
 */
import type { Actor, FlowEngine } from '@zerodep-dev/flow-core';

export interface FlowJobsOptions {
  /** 多久看一次，默认 10 秒（同 Spring 的 flow.jobs.interval） */
  readonly intervalMs?: number;
  /** 幂等记录留多久，默认 24 小时（同 Spring 的 flow.idempotency.retention） */
  readonly retentionSeconds?: number;
  /** 一轮出错（数据库暂时连不上……）记到哪；下一轮再试。默认 console.error */
  readonly onError?: (error: unknown) => void;
}

/** 正在跑的轮询；stop 之后等手上这一轮做完 */
export interface FlowJobs {
  stop(): Promise<void>;
}

/** 多久删一次过期的幂等记录 */
const PURGE_EVERY_MS = 60 * 60 * 1000;

export function startFlowJobs<U extends Actor>(engine: FlowEngine<U>, options: FlowJobsOptions = {}): FlowJobs {
  const intervalMs = options.intervalMs ?? 10_000;
  const retentionSeconds = options.retentionSeconds ?? 24 * 60 * 60;
  const onError = options.onError ?? ((error) => console.error(`flow job polling failed; retrying in ${intervalMs} ms`, error));
  let running = true;
  // 上次删幂等记录的时间：启动后的第一轮就删一次
  let lastPurge = -Infinity;
  let current: Promise<void> = Promise.resolve();

  const poll = async () => {
    try {
      while (running && (await engine.runDueJobs()) > 0) {
        // 一批做完马上看还有没有：积压时尽快做完
      }
      if (running && Date.now() - lastPurge >= PURGE_EVERY_MS) {
        lastPurge = Date.now();
        await engine.purgeRequests(retentionSeconds);
      }
    } catch (error) {
      onError(error);
    }
  };

  // 上一轮做完才排下一轮（同 scheduleWithFixedDelay），慢的一轮不会和下一轮叠在一起
  let timer: NodeJS.Timeout;
  const schedule = () => {
    timer = setTimeout(() => {
      current = poll().then(() => {
        if (running) schedule();
      });
    }, intervalMs);
    timer.unref();
  };
  schedule();

  return {
    async stop() {
      running = false;
      clearTimeout(timer);
      await current;
    },
  };
}
