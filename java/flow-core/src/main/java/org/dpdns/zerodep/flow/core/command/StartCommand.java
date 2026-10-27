package org.dpdns.zerodep.flow.core.command;

import lombok.Data;
import org.dpdns.zerodep.flow.core.engine.state.Variables;
import org.dpdns.zerodep.flow.core.spi.FormValues;

import java.util.List;
import java.util.Map;

/** 发起一个实例（docs/decisions/0006 的 3.1）：按这个流程最新发布的版本走；从发起前的草稿发起时带上 {@link #draftId}。 */
@Data
public class StartCommand implements FlowCommand {
    /** 流程标识 */
    private final String key;
    /** 表单的值：只取开始节点上能改的字段 */
    private final FormValues form;
    /** 流程变量的初始值，盖过定义里的默认值 */
    private final Variables variables;
    /** 业务单据编号；同一个编号可以发起多次（重新提交） */
    private final String businessKey;
    /** 实例标题；不写时按定义的模板生成 */
    private final String title;
    /** 紧急程度，0 是常规，越大越急 */
    private final int priority;
    /** 第一步要发起人选处理人的节点（0005 的 5.6 {@code pick}）→ 选的人 */
    private final Map<String, List<Long>> picks;
    /** 从这份草稿发起（{@link SaveStartDraftCommand} 存的）：用草稿那个实例，没提交的字段用草稿里的值；不是从草稿时为 null */
    private final Long draftId;

    /** 不从草稿发起 */
    public StartCommand(String key, FormValues form, Variables variables, String businessKey, String title, int priority, Map<String, List<Long>> picks) {
        this(key, form, variables, businessKey, title, priority, picks, null);
    }

    /** 补上空的表单、变量与选人 */
    public StartCommand(String key, FormValues form, Variables variables, String businessKey, String title, int priority, Map<String, List<Long>> picks,
                        Long draftId) {
        form = form == null ? FormValues.empty() : form;
        variables = variables == null ? Variables.empty() : variables;
        picks = picks == null ? Map.of() : Map.copyOf(picks);
        this.key = key;
        this.form = form;
        this.variables = variables;
        this.businessKey = businessKey;
        this.title = title;
        this.priority = priority;
        this.picks = picks;
        this.draftId = draftId;
    }

    /** 只给流程标识与表单的值 */
    public static StartCommand of(String key, Map<String, ?> form) {
        return new StartCommand(key, FormValues.of(form), Variables.empty(), null, null, 0, Map.of());
    }
}
