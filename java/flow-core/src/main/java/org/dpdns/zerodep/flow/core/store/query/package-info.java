/**
 * 存储的默认实现：用 query 读写建表脚本（resources 下的 db/flow/mysql）建的表。
 * <pre>
 *   FlowDatabase     引擎自己的数据库实例：租户条件、修改时间由它统一处理
 *   FlowTables       表的声明（query 的表接口）：列直接声明成引擎里的类型
 *   Columns          列的转换器：时间（Instant ↔ UTC）、JSON 列（表单值、流程变量、运行状态……）
 *   QueryStores      把六个默认存储组装起来
 *   Query*Store      六个存储各自的实现
 * </pre>
 * 读写的套路都一样：读用 {@code selectAs(引擎的记录.class)}，插入用 {@code insertInto(表).values(记录)}（自增的编号用
 * {@code insertKeys} 取回），整行写回用 {@code update(表).byId(记录)}，状态的变化用带条件的 {@code update(表).set(...).where(...)}。
 */
package org.dpdns.zerodep.flow.core.store.query;
