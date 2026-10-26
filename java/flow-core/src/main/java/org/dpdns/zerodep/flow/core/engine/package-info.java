/**
 * 引擎内部：一个命令怎么跑。接入方不直接用这里的类（经 {@code FlowEngine}）。
 * <pre>
 *   处理类（一个命令一个）         做什么
 *   StartHandler                  发起：找版本、写实例、收表单，从开始节点往前走
 *   ClaimHandler                  认领：先到先得（带条件的更新）
 *   CompleteHandler               处理：锁实例、核对结果与意见、收表单、待办改成已处理，按结果往前走
 *   WithdrawHandler               撤回：按定义的规则看能不能撤，取消开着的待办
 *   Publishing                    发布流程定义与表单：先校验，再交给存储定版本号
 *   Queries                       查询：收件箱、实例详情
 *
 *   它们共用的                    做什么
 *   Execution                     让实例往前走（选线 → 进节点 → 生成待办或结束），最后一次写回
 *   AssigneeResolver / Assignees  人工节点由谁处理：人与收件箱
 *   CandidateRules                谁算候选人、谁能发起
 *   FormSubmission                收表单：按节点上的字段权限合并、校验
 *   InstanceDisplay               列表上显示的标题、摘要列、正在处理的节点
 *   DefinitionCache               发布的版本解析一次就记住
 *   FlowErrors                    常用的带业务错误码的异常
 *   EngineContext                 上面这些共用的：数据库、存储、时钟、组织架构
 * </pre>
 * 每个处理类的类注释按顺序写着它的步骤，代码里用同样的序号标出，可以对照着读。
 */
package org.dpdns.zerodep.flow.core.engine;
