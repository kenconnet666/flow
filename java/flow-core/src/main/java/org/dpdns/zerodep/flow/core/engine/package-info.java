/**
 * 引擎内部：一个命令怎么跑。接入方不直接用这里的类（经 {@code FlowEngine}）；类是 public 只是为了分包，不是对外的接口。
 * <pre>
 *   engine            推进的核心
 *     Execution         让实例往前走（选线 → 进节点 → 生成待办或结束），最后一次写回
 *     EngineContext     共用的：easy-query、时钟、组织架构、扩展、事件分发、定义缓存
 *     EventDispatcher   流转记录交给监听、通知交给渠道，管着事务边界
 *     DefinitionCache   发布的版本解析一次就记住（LoadedDefinition）
 *     FlowErrors        常用的带业务错误码的异常
 *   engine.node       每种节点到达时做什么：人工（含会签、生成待办）、网关、定时、自动、等事件、调用流程、抄送
 *   engine.handler    每个命令一个处理类：发起、认领、处理、撤回、撤回一步、协作、管理员、外部事件、发布
 *   engine.rule       流转规则：会签怎么算结果、委派加签的子待办、退回能选的节点、标题与摘要列、截止时间
 *   engine.assignee   处理人：按来源解析成人与收件箱、谁算候选、发起与处理时的选人预览
 *   engine.form       表单：按节点权限合并提交、引用（form.x、sys.x、流程变量）怎么取值
 *   engine.job        作业：轮询抢占（JobRunner）与内置的定时、超时、提醒、无人认领、异步动作
 *   engine.query      查询：收件箱、已办、抄送、实例详情、管理端的列表，流程图的视图
 *   engine.store      几处共用的读写：锁实例、表单的值；取消待办、带条件地改待办的状态
 *   engine.notify     按流转记录算出要发的通知
 * </pre>
 * 每个处理类的类注释按顺序写着它的步骤，代码里用同样的序号标出，可以对照着读。
 */
package org.dpdns.zerodep.flow.core.engine;

