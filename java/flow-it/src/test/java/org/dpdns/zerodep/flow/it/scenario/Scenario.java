package org.dpdns.zerodep.flow.it.scenario;

import lombok.Data;
import org.dpdns.zerodep.flow.it.support.TestUser;
import org.dpdns.zerodep.wire.Json;
import org.dpdns.zerodep.wire.Wire;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** 一个场景用例（spec/scenarios 下的一个文件，格式见那里的 README）：先发布的表单与流程、固定时钟、操作人、一串步骤。 */
@Data
class Scenario {
    /** 文件名（测试名里显示） */
    private final String file;
    /** 一句话说明 */
    private final String name;
    /** 先发布的表单 */
    private final List<FormFile> forms;
    /** 再发布的流程定义（JSON 文字） */
    private final List<String> processes;
    /** 固定时钟的时刻 */
    private final Instant clock;
    /** 名字 → 操作人 */
    private final Map<String, TestUser> actors;
    /** 步骤 */
    private final List<Step> steps;

    /** spec/scenarios 目录（测试的工作目录是 java/flow-it） */
    static final Path DIR = Path.of("..", "..", "spec", "scenarios");

    /** 一张要发布的表单（forms 下的文件）。 */
    @Data
    static class FormFile {
        /** 表单标识 */
        private final String key;
        /** 名称 */
        private final String name;
        /** 字段清单的 JSON 文字 */
        private final String manifest;
    }

    /** 一个步骤。 */
    @Data
    static class Step {
        /** 给后面的步骤引用的名字；可以不写 */
        private final String id;
        /** 操作人的代号（{@code actors} 的键） */
        private final String as;
        /** 命令或查询 */
        private final String command;
        /** 参数（按命令不同，见 README） */
        private final Map<String, Object> args;
        /** 期望；没写时只要求不出错 */
        private final Map<String, Object> expect;
    }

    /** 目录下的全部场景文件，按名字排序 */
    static List<String> files() {
        try (Stream<Path> paths = Files.list(DIR)) {
            return paths.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 读一个场景文件 */
    static Scenario read(String file) {
        // 场景文件同线上的请求：typed JSON，按 wire 还原（没标类型的数是 JavaScript 的数）
        Map<String, Object> tree = object(Wire.fromJson(text(DIR.resolve(file))), file);
        List<FormFile> forms = new ArrayList<>();
        for (Object path : list(tree.get("forms"))) {
            Map<String, Object> form = object(Json.read(text(DIR.resolve((String) path))), (String) path);
            forms.add(new FormFile((String) form.get("key"), (String) form.get("name"), Json.write(form.get("manifest"))));
        }
        List<String> processes = list(tree.get("processes")).stream().map(path -> text(DIR.resolve((String) path))).toList();
        Map<String, TestUser> actors = new LinkedHashMap<>();
        object(tree.get("actors"), "actors").forEach((alias, value) -> actors.put(alias, actor(alias, object(value, alias))));
        List<Step> steps = new ArrayList<>();
        for (Object step : list(tree.get("steps"))) {
            Map<String, Object> s = object(step, "step");
            steps.add(new Step((String) s.get("id"), (String) s.get("as"), (String) s.get("command"),
                s.get("args") == null ? Map.of() : object(s.get("args"), "args"), s.get("expect") == null ? Map.of() : object(s.get("expect"), "expect")));
        }
        return new Scenario(file, (String) tree.get("name"), forms, processes, Instant.parse((String) tree.get("clock")), actors, steps);
    }

    /** 一个操作人：代号 → 编号、名字、角色、部门（编号都是整数） */
    private static TestUser actor(String alias, Map<String, Object> fields) {
        String name = fields.get("name") == null ? alias : (String) fields.get("name");
        return new TestUser(id(fields.get("id"), alias), name, ids(fields.get("departments"), alias), ids(fields.get("roles"), alias));
    }

    private static LinkedHashSet<Long> ids(Object value, String alias) {
        LinkedHashSet<Long> out = new LinkedHashSet<>();
        for (Object item : list(value)) out.add(id(item, alias));
        return out;
    }

    private static Long id(Object value, String alias) {
        if (!(value instanceof Number n)) throw new IllegalArgumentException("actor " + alias + ": ids must be whole numbers, got " + value);
        return n.longValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String where) {
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException(where + ": expected an object");
        return (Map<String, Object>) value;
    }

    private static List<?> list(Object value) { return value == null ? List.of() : (List<?>) value; }

    private static String text(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String toString() { return file + "：" + name; }
}
