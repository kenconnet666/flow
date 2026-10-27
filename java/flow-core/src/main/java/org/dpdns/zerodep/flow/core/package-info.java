/**
 * 流程引擎（Java）。接入方只用 {@link org.dpdns.zerodep.flow.core.FlowEngine}：组装一次，调它的命令与查询。
 * <p>包的分工，从外到里：
 * <pre>
 *   FlowEngine        门面与组装：每个操作先问权限，再交给 engine 包里对应的处理类
 *   identity          接入方实现的：操作人（Actor）、当前操作人、组织架构、权限
 *   command           命令的参数与结果（每个命令一个类）、命令拦截器
 *   data              形状由接入方定的数据：表单的值、流程变量；引擎内部的运行状态
 *   engine            引擎内部：handler 里每个命令一个处理类，实例往前走在 Execution（子包的分工见 engine 的包说明）
 *   entity            表（easy-query 的实体，建表脚本见 resources 下的 db/flow/mysql）：写库用它们
 *   view              给前端与接入方的形状：按 select(XxxVO.class) 读出的 VO 与拼起来的视图，按对象分 workitem、instance、definition、form
 *   extension         接入方扩展：处理人解析器、守卫、自动节点的动作、作业的处理
 *   listener、notify  监听流转记录；通知的渠道
 *   calendar          工作日历
 *   db                接入方的 easy-query 上要装的东西：JSON 列的转换器、时间、租户与修改时间的拦截器
 * </pre>
 * 读代码建议的顺序：FlowEngine → engine.handler.StartHandler / CompleteHandler（一个命令的步骤）→ engine.Execution（实例怎么往前走）
 * → entity 与 view。规则（条件、选线、字段权限、校验）在模型包 flow-model，前后端共用。
 */
package org.dpdns.zerodep.flow.core;

