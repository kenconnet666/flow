package org.dpdns.zerodep.flow.core.view;

import lombok.Data;
import org.dpdns.zerodep.flow.model.definition.FieldAccess;
import org.dpdns.zerodep.flow.model.definition.NodeFormAccess;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 表单在某个位置（发起、某个待办）的样子：前端的表单容器拿它渲染（0004 的 4.4）。
 * <p>{@code access} 是节点上写的权限，前端用模型包的 {@code fieldStates} 合算（显示条件随填写实时变，所以不在这里算死）；
 * 值里已经去掉节点上隐藏的字段（0004 的 4.5）。
 * <p>前端优先用编译好的代码；编译器的大版本不对、或者只有源码时在浏览器里重新编译（0004 的 5.4）；都没有时按字段清单自动排版。
 */
@Data
public class FormView {
    /** 表单版本的编号 */
    private final Long versionId;
    /** 表单标识 */
    private final String key;
    /** 版本号 */
    private final int version;
    /** 字段清单（0004 的 1.3） */
    private final Map<String, Object> manifest;
    /** 前端源码；没有时为 null */
    private final String source;
    /** 编译好的代码（浏览器里能直接执行的）；没有时为 null */
    private final String compiled;
    /** 编译它的编译器版本 */
    private final String compilerVersion;
    /** 节点上的权限：{@code { default, fields }}，同 0005 的节点 {@code form} */
    private final Map<String, Object> access;
    /** 是不是发起：没写 default 时发起能改、其他节点只读 */
    private final boolean start;
    /** 表单的值（去掉了隐藏的字段） */
    private final Map<String, Object> values;

    /** 节点上的权限写成定义里的 JSON 形状：{@code { default: "readonly", fields: { amount: "editable" } }} */
    public static Map<String, Object> accessJson(NodeFormAccess access) {
        Map<String, Object> json = new LinkedHashMap<>();
        if (access == null) return json;
        if (access.getDefaultAccess() != null) json.put("default", access.getDefaultAccess().json());
        Map<String, Object> fields = new LinkedHashMap<>();
        for (Map.Entry<String, FieldAccess> e : access.getFields().entrySet()) fields.put(e.getKey(), e.getValue().json());
        if (!fields.isEmpty()) json.put("fields", fields);
        return json;
    }
}
