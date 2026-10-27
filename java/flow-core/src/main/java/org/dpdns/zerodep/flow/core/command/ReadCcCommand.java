package org.dpdns.zerodep.flow.core.command;

import lombok.Data;

/** 读了抄送：把一个实例抄送给我的都标成已读（"抄送我的"里打开它时）。 */
@Data
public class ReadCcCommand implements FlowCommand {
    /** 实例 */
    private final Long instanceId;
}
