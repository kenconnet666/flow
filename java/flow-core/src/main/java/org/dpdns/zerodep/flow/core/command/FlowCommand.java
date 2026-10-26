package org.dpdns.zerodep.flow.core.command;

/**
 * 引擎的一个命令（0006 第 3 节）：发起、处理、协作、管理员命令……每个是一个记录。命令都进同一条管道
 * （权限 → {@link CommandInterceptor} → 标准处理，在一个事务里），拦截器按具体的类型认它：
 * {@code if (command instanceof CompleteCommand complete) ...}
 */
public interface FlowCommand { }
