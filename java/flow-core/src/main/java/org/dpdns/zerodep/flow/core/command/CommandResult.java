package org.dpdns.zerodep.flow.core.command;

import org.dpdns.zerodep.flow.model.runtime.FlowEvent;
import org.dpdns.zerodep.flow.model.runtime.ProcessInstance;
import org.dpdns.zerodep.flow.model.runtime.WorkItem;

import java.util.List;

/**
 * 一个命令改了什么（0006 第 1 节）：前端据此刷新，不用再查一次。
 *
 * @param instance 实例的新状态
 * @param created  新生成的待办
 * @param closed   这次结束的待办（处理完的、取消的），是改之前的样子
 * @param events   这次写的流转记录
 */
public record CommandResult(ProcessInstance instance, List<WorkItem> created, List<WorkItem> closed, List<FlowEvent> events) {
    /** 复制并冻结列表 */
    public CommandResult {
        created = List.copyOf(created);
        closed = List.copyOf(closed);
        events = List.copyOf(events);
    }
}
