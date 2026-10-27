/**
 * 流程引擎（Java）。接入方只用 {@link org.dpdns.zerodep.flow.core.FlowEngine}：组装一次，调它的命令与查询。
 * <p>包的分工，从外到里：
 * <pre>
 *   FlowEngine        门面与组装：每个操作先问权限，再交给 engine 包里对应的处理类
 *   identity          接入方实现的：操作人（Actor）、当前操作人、组织架构、权限
 *   command           命令的参数与结果（每个命令一个 record）
 *   data              形状由接入方定的数据：表单的值、流程变量；引擎内部的运行状态
 *   engine            引擎内部：每个命令一个处理类，实例往前走在 Execution，几处共用的读写在 Instances、WorkItems
 *   entity            表（easy-query 的实体，建表脚本见 resources 下的 db/flow/mysql）：写库用它们
 *   view              给前端与接入方的形状：按 select(XxxVO.class) 读出的 VO，以及拼起来的视图（record）
 *   db                接入方的 easy-query 上要装的东西：JSON 列的转换器、时间、租户与修改时间的拦截器
 * </pre>
 * 读代码建议的顺序：FlowEngine → engine.StartHandler / CompleteHandler（一个命令的步骤）→ engine.Execution（实例怎么往前走）
 * → entity 与 view。规则（条件、选线、字段权限、校验）在模型包 flow-model，前后端共用。
 */
package org.dpdns.zerodep.flow.core;
