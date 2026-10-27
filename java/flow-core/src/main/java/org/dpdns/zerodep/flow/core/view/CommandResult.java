package org.dpdns.zerodep.flow.core.view;

import lombok.Data;
import org.dpdns.zerodep.flow.core.view.instance.FlowEventVO;
import org.dpdns.zerodep.flow.core.view.instance.InstanceVO;
import org.dpdns.zerodep.flow.core.view.workitem.WorkItemVO;

import java.util.List;

/** 一个命令改了什么（0006 第 1 节）：前端据此刷新，不用再查一次。 */
@Data
public class CommandResult {
    /** 实例的新状态 */
    private final InstanceVO instance;
    /** 新生成的待办 */
    private final List<WorkItemVO> created;
    /** 这次结束的待办（处理完的、取消的），是改之前的样子 */
    private final List<WorkItemVO> closed;
    /** 这次写的流转记录 */
    private final List<FlowEventVO> events;

    /** 复制并冻结列表 */
    public CommandResult(InstanceVO instance, List<WorkItemVO> created, List<WorkItemVO> closed, List<FlowEventVO> events) {
        created = List.copyOf(created);
        closed = List.copyOf(closed);
        events = List.copyOf(events);
        this.instance = instance;
        this.created = created;
        this.closed = closed;
        this.events = events;
    }
}
