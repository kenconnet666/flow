/**
 * 流程引擎（Java）。接入方只用 {@link org.dpdns.zerodep.flow.core.FlowEngine}：组装一次，调它的命令与查询。
 * <p>包的分工，从外到里：
 * <pre>
 *   FlowEngine        门面与组装：每个操作先问权限，再交给 engine 包里对应的处理类
 *   identity          接入方实现的：操作人（Actor）、当前操作人、组织架构、权限
 *   command           命令的参数与结果（每个命令一个 record）
 *   data              形状由接入方定的数据：表单的值、流程变量；引擎内部的运行状态
 *   engine            引擎内部：每个命令一个处理类，实例往前走在 Execution
 *   store             存储接口（六个），每个都能单独替换
 *   store.query       存储的默认实现：用 query 读写建表脚本建的表
 * </pre>
 * 读代码建议的顺序：FlowEngine → engine.StartHandler / CompleteHandler（一个命令的步骤）→ engine.Execution（实例怎么往前走）
 * → store 的接口 → store.query 的实现。规则（条件、选线、字段权限、校验）在模型包 flow-model，两个后端共用。
 */
package org.dpdns.zerodep.flow.core;
