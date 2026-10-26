/**
 * 存储的默认实现：用 query 读写建表脚本（resources 下的 db/flow/mysql）建的表。
 * <pre>
 *   FlowDatabase     引擎自己的数据库实例：租户条件、修改时间由它统一处理
 *   FlowTables       表的声明（query 的表接口）：列直接声明成引擎里的类型
 *   Columns          列的转换器：时间（Instant ↔ UTC）、JSON 列（表单值、流程变量、运行状态……）
 *   QueryStores      把六个默认存储组装起来
 *   Query*Store      六个存储各自的实现
 * </pre>
 * 读写的套路都一样，没有逐个字段的赋值：
 * <pre>
 *   读一行            db.optional(from(表).where(...).selectAs(引擎的记录.class))
 *   插入并拿回编号    db.insertReturning(insertInto(表).values(记录))       record 换上自增的编号
 *   整行写回          db.execute(update(表).byId(记录))
 *   状态的变化        db.execute(update(表).set(要改的几列的记录).set(r -> 版本加一).where(只在某状态时))   改到 0 行就是别人先改了
 * </pre>
 * 记录的属性名就是列名（{@code completedBy} ↔ {@code completed_by}），query 按名字对上。
 */
package org.dpdns.zerodep.flow.core.store.query;
