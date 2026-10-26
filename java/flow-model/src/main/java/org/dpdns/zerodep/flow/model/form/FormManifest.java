package org.dpdns.zerodep.flow.model.form;

import org.dpdns.zerodep.flow.model.Condition;
import org.dpdns.zerodep.flow.model.Conditions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 表单的字段清单（docs/decisions/0004 的 1.3）：表单源码里 {@code defineFields({...})} 的字面量，保存时提取成 JSON 存下。
 * 与 TS 模型包的 {@code manifest.ts} 同一份形状；字段按清单里的先后。
 */
public final class FormManifest {
    private final Map<String, Field> fields;

    private FormManifest(Map<String, Field> fields) { this.fields = Collections.unmodifiableMap(fields); }

    /**
     * 一个字段。类型专有的属性按 JSON 原样放在 {@code attributes} 里（{@code minLength}、{@code scale}、{@code options}……），
     * 引擎用到的几样有自己的字段。
     *
     * @param label        显示名
     * @param type         类型：内置的十种，或接入方注册的
     * @param required     必填
     * @param multiple     select 能不能多选
     * @param visibleWhen  显示条件；null 时总是显示
     * @param requiredWhen 必填条件；null 时只看 required
     * @param columns      list 的列；不是 list 时为 null
     * @param attributes   字段的全部属性（JSON 原样）
     */
    public record Field(String label, String type, boolean required, boolean multiple, Condition visibleWhen,
                        Condition requiredWhen, FormManifest columns, Map<String, Object> attributes) { }

    /** 从 JSON 树读字段清单；形状不对时报 IllegalArgumentException，消息里带路径 */
    public static FormManifest parse(Object tree) {
        return parse(tree, "$");
    }

    private static FormManifest parse(Object tree, String path) {
        if (!(tree instanceof Map<?, ?> map)) throw new IllegalArgumentException(path + ": a form manifest must be an object");
        Map<String, Field> fields = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String name = String.valueOf(entry.getKey());
            String at = path + "." + name;
            if (!(entry.getValue() instanceof Map<?, ?> attributes)) throw new IllegalArgumentException(at + ": a field must be an object");
            if (!(attributes.get("type") instanceof String type)) throw new IllegalArgumentException(at + ".type: must be a string");
            Object label = attributes.get("label");
            Object visible = attributes.get("visibleWhen");
            Object requiredWhen = attributes.get("requiredWhen");
            Object columns = attributes.get("fields");
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = (Map<String, Object>) attributes;
            fields.put(name, new Field(
                label == null ? name : label.toString(), type,
                Boolean.TRUE.equals(attributes.get("required")), Boolean.TRUE.equals(attributes.get("multiple")),
                visible == null ? null : Conditions.parse(visible), requiredWhen == null ? null : Conditions.parse(requiredWhen),
                "list".equals(type) && columns != null ? parse(columns, at + ".fields") : null, raw));
        }
        return new FormManifest(fields);
    }

    /** 全部顶层字段，按清单里的先后 */
    public Map<String, Field> fields() { return fields; }

    /** 按路径找字段：{@code amount}，或明细表的列 {@code lines.price}；没有时为 null */
    public Field field(String path) {
        int dot = path.indexOf('.');
        if (dot < 0) return fields.get(path);
        Field list = fields.get(path.substring(0, dot));
        return list != null && list.columns() != null ? list.columns().field(path.substring(dot + 1)) : null;
    }

    /** 全部字段的路径，明细表的列写成 {@code 列表.列} */
    public List<String> paths() {
        List<String> paths = new ArrayList<>();
        for (Map.Entry<String, Field> entry : fields.entrySet()) {
            paths.add(entry.getKey());
            if (entry.getValue().columns() != null) {
                for (String column : entry.getValue().columns().fields().keySet()) paths.add(entry.getKey() + "." + column);
            }
        }
        return paths;
    }
}
