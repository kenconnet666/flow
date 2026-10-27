package org.dpdns.zerodep.flow.model.error;

import lombok.Data;
import org.dpdns.zerodep.flow.model.form.FormAccess;
import org.dpdns.zerodep.flow.model.validate.Problem;

import java.util.Map;

/**
 * 错误明细的一项（0008 第 1 节响应体的 {@code errors}）：表单校验是"哪个字段、什么问题"，发布校验是"哪个节点或连线、什么问题"。
 * 与 TS 模型包的 {@code ErrorDetail} 同形；没有的部分为 null，写成 JSON 时省掉。
 */
@Data
public class ErrorDetail {
    /** 问题的代号：表单的值问题（{@code required}、{@code max}……）或 0005 第 7 节的问题代号 */
    private final String code;
    /** 表单字段 */
    private final String field;
    /** 节点 */
    private final String node;
    /** 连线 */
    private final String edge;
    /** 泳道 */
    private final String lane;
    /** 文案里要填的参数 */
    private final Map<String, String> params;

    /** 复制并冻结参数 */
    public ErrorDetail(String code, String field, String node, String edge, String lane, Map<String, String> params) {
        params = params == null ? Map.of() : Map.copyOf(params);
        this.code = code;
        this.field = field;
        this.node = node;
        this.edge = edge;
        this.lane = lane;
        this.params = params;
    }

    /** 某个字段的问题 */
    public static ErrorDetail field(String field, String code) {
        return new ErrorDetail(code, field, null, null, null, Map.of());
    }

    /** 某个节点的问题 */
    public static ErrorDetail node(String node, String code) {
        return new ErrorDetail(code, null, node, null, null, Map.of());
    }

    /** 收表单时的问题 */
    public static ErrorDetail of(FormAccess.SubmissionError error) {
        return field(error.getField(), error.getCode());
    }

    /** 定义或字段清单的校验问题 */
    public static ErrorDetail of(Problem problem) {
        return new ErrorDetail(problem.getCode(), problem.getField(), problem.getNode(), problem.getEdge(), problem.getLane(), problem.getParams());
    }
}
