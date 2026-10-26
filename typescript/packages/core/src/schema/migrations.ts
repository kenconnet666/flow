/**
 * 引擎的表（同 Spring 的 `flow.flyway.enabled`）：建表脚本与 Java flow-core 的是同一份（{@link MYSQL_SCRIPTS}，由 scripts/sync-sql.mjs 生成）。
 * 用 query 的迁移器执行，历史记在 `flow_migration`，与应用自己的迁移分开：
 *
 *   await migrateFlowSchema(mysqlDriver(pool));   // 部署前跑一次，或应用启动时
 *
 * 同一个库只由一个后端建表：Java 一侧用 Flyway（历史表 flow_schema_history），两边的历史互不认。
 */
import { database, migration, type Driver, type Migration } from '@zerodep-dev/query-core';
import { MYSQL_SCRIPTS } from './mysql-scripts.js';

/** 历史表 */
export const FLOW_MIGRATION_HISTORY = 'flow_migration';

/** 建表脚本读成迁移：`V7__create_flow_step.sql` 是版本 7；脚本里的语句以行尾的分号分开 */
export function flowMigrations(): Migration[] {
  return MYSQL_SCRIPTS.map(({ file, sql }) => {
    const [, version, name] = /^V(\d+)__(.+)\.sql$/.exec(file)!;
    const statements = sql
      .replace(/^\s*--.*$/gm, '')
      .split(/;\s*(?:\n|$)/)
      .filter((s) => s.trim() !== '');
    return migration(version!, name!.replaceAll('_', ' '), (steps) => {
      for (const statement of statements) steps.sql(statement);
    });
  });
}

/** 执行还没执行过的建表脚本，返回这次执行的版本号 */
export function migrateFlowSchema(driver: Driver): Promise<string[]> {
  return database(driver).migrator(flowMigrations()).historyTable(FLOW_MIGRATION_HISTORY).migrateToLatest();
}
