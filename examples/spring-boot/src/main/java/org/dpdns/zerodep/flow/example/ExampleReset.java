package org.dpdns.zerodep.flow.example;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 示例的"重置演示"：清空引擎的数据（{@code flow_} 开头的表，建表历史 {@code flow_schema_history} 除外）与上传的附件，回到刚建好表的样子；
 * 示例的流程、表单与几单在办的数据由示例前端随后发布（apps/demo/seed/demo-seed.ts，与 {@code pnpm seed} 同一份）。
 * 部署出去给人试用的演示站点上谁都能点它，所以只在 {@code demo.reset.enabled} 为 true 时有这个接口。
 * <p>用 DELETE 而不是 TRUNCATE：编号接着往上涨，引擎按编号记住的已发布版本（DefinitionCache）不会和重置后新发布的撞上。
 */
@RestController
@ConditionalOnProperty(name = "demo.reset.enabled", havingValue = "true")
public class ExampleReset {
    private final JdbcTemplate jdbc;

    public ExampleReset(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 清空：返回清了几张表 */
    @PostMapping("/demo/reset")
    @Transactional
    public Map<String, Object> reset() throws IOException {
        List<String> tables = jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name LIKE 'flow!_%' ESCAPE '!' AND table_name <> 'flow_schema_history'",
            String.class);
        for (String table : tables) jdbc.update("DELETE FROM `" + table + "`");
        ExampleFiles.clear();
        return Map.of("code", "ok", "data", Map.of("tables", tables.size()));
    }
}
