package org.dpdns.zerodep.flow.core.command;

/**
 * 读了抄送：把一个实例抄送给我的都标成已读（"抄送我的"里打开它时）。
 *
 * @param instanceId 实例
 */
public record ReadCcCommand(Long instanceId) implements FlowCommand { }
