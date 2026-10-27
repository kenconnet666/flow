package org.dpdns.zerodep.flow.model.definition;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * 流程定义 JSON 的格式版本（定义里的 {@code schema}，0005 第 1 节）。已发布的版本存的是发布时的原样，引擎升级后还要读得懂：
 * 读之前按 {@link #STEPS} 一步步升到 {@link #CURRENT}。不写 {@code schema} 的是第 1 版。
 * <p>
 * 改定义格式（改名、换结构）时：{@code CURRENT} 加一，在 {@code STEPS} 末尾加一步"上一版 → 这一版"（只动 JSON 树、不丢信息），
 * TS 模型包的 {@code schema.ts} 同样加一步，规格用例（spec/definitions）加一份旧格式的定义。只加字段、不改原有字段的意思时不用加版本。
 * <p>
 * 比 {@code CURRENT} 还新的定义（更新的引擎发布的）读不懂，报错，不硬读。
 */
public class DefinitionSchema {
    /** 这个引擎写的格式 */
    public static final int CURRENT = 1;

    /** 第 i 个是第 i+1 版 → 第 i+2 版；现在只有第 1 版，没有要升的 */
    private static final List<UnaryOperator<Map<String, Object>>> STEPS = List.of();

    private DefinitionSchema() { }

    /** 定义的格式比这个引擎还新（更新的引擎发布的）：问题代号 {@code schema-newer} */
    @Getter
    public static class Newer extends IllegalArgumentException {
        /** 定义写的版本 */
        private final int version;

        public Newer(int version) {
            super("$.schema: format " + version + " is newer than this engine understands (" + CURRENT + "); upgrade the engine");
            this.version = version;
        }
    }

    /** 定义写的是第几版：没写时是 1；写的不是正整数时报 IllegalArgumentException */
    public static int versionOf(Map<?, ?> tree) {
        Object schema = tree.get("schema");
        if (schema == null) return 1;
        if (!(schema instanceof Number n) || n.doubleValue() != Math.floor(n.doubleValue()) || n.intValue() < 1) {
            throw new IllegalArgumentException("$.schema: must be a positive integer");
        }
        return n.intValue();
    }

    /**
     * 升到当前的格式：返回一棵新树（{@code schema} 是 {@link #CURRENT}），原来的不改；已经是当前格式时原样返回。
     * 比当前还新时报 {@link Newer}。
     */
    public static Map<?, ?> upgrade(Map<?, ?> tree) {
        int version = versionOf(tree);
        if (version > CURRENT) throw new Newer(version);
        if (version == CURRENT) return tree;
        Map<String, Object> copy = new LinkedHashMap<>();
        tree.forEach((key, value) -> copy.put(String.valueOf(key), value));
        Map<String, Object> out = copy;
        for (int step = version - 1; step < CURRENT - 1; step++) out = STEPS.get(step).apply(out);
        out.put("schema", CURRENT);
        return out;
    }
}
