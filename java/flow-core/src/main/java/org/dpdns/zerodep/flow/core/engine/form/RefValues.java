package org.dpdns.zerodep.flow.core.engine.form;

import org.dpdns.zerodep.flow.model.definition.Ref;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** 定义里写的参数（解析器、动作的 {@code args}）：其中的 {@code { ref }} 换成值，对象、列表里的也换 */
public class RefValues {
    private RefValues() { }

    /** 换好引用的参数 */
    public static Map<String, Object> resolve(Map<String, Object> args, Function<String, Object> read) {
        Map<String, Object> out = new LinkedHashMap<>();
        args.forEach((key, value) -> out.put(key, value(value, read)));
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object value(Object value, Function<String, Object> read) {
        if (value instanceof Ref ref) return read.apply(ref.getRef());
        if (value instanceof Map<?, ?> map) return resolve((Map<String, Object>) map, read);
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object item : list) out.add(value(item, read));
            return out;
        }
        return value;
    }
}
