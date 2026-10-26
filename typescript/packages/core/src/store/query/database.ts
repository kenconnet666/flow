/**
 * 引擎自己的 query 数据库实例：驱动由接入方给（mysql2 的连接池经 `mysqlDriver(pool)`），配置由引擎定。同 Java 的 FlowDatabase，
 * 把每张表都要做的事交给 query 统一做：
 *   - 租户：带租户列的表（Tenanted）在查询、更新、删除里自动加 `tenant_id = 当前租户`，插入时自动填；
 *   - 修改时间：带修改时间的表（Touched）在插入、更新时自动填当前时刻。
 */
import { database, filter, onInsert, onUpdate, type Database, type Driver } from '@zerodep-dev/query-core';
import { instantOf, type Clock } from '../../time.js';
import { Tenanted, Touched, type At } from './tables.js';

/** 租户过滤器的名字：跨租户的读写（作业的领取、过期记录的清理）用 `unfiltered(TENANT_FILTER)` 关掉它 */
export const TENANT_FILTER = 'flow-tenant';

/** 在这个驱动上建引擎用的数据库实例；tenant 给出当前租户，不用多租户时是 `() => 0n` */
export function flowDatabase(driver: Driver, tenant: () => bigint, clock: Clock): Database {
  return database(driver, {
    filters: [filter(TENANT_FILTER, Tenanted, (t) => t.tenantId.eq(tenant()))],
    fills: [
      onInsert(Tenanted, (t) => t.tenantId, tenant),
      onInsert<Touched, At>(Touched, (t) => t.updatedAt, () => instantOf(clock.now()) as At),
      onUpdate<Touched, At>(Touched, (t) => t.updatedAt, () => instantOf(clock.now()) as At),
    ],
  });
}
