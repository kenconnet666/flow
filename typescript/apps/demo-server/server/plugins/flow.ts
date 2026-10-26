/**
 * 启动时建表（同 Spring 的 flow.flyway.enabled；FLOW_MIGRATE=false 时不建，例如表已由 Java 一侧的 Flyway 建好），
 * 建好后定时做到期的作业：每 5 秒看一次（同 Spring 示例的 flow.jobs.interval），关闭时停掉。
 */
import { migrateFlowSchema } from '@zerodep-dev/flow-core';
import { startFlowJobs, type FlowJobs } from '@zerodep-dev/flow-nuxt';
import { defineNitroPlugin } from 'nitropack/runtime';
import { driver, engine } from '../flow';

async function migrate(): Promise<void> {
  if (process.env['FLOW_MIGRATE'] === 'false') return;
  const applied = await migrateFlowSchema(driver);
  if (applied.length > 0) console.info(`flow tables migrated: ${applied.join(', ')}`);
}

export default defineNitroPlugin((nitro) => {
  let jobs: FlowJobs | undefined;
  // Nitro 不等插件里的异步：建表期间来的请求在 request 钩子里等着
  const ready = migrate().then(() => {
    jobs = startFlowJobs(engine, { intervalMs: 5_000 });
  });
  ready.catch((error: unknown) => console.error('flow tables migration failed', error));
  nitro.hooks.hook('request', () => ready);
  nitro.hooks.hook('close', () => jobs?.stop());
});
