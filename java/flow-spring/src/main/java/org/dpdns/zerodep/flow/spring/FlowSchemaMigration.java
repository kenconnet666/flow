package org.dpdns.zerodep.flow.spring;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

import javax.sql.DataSource;

/**
 * 启动时用 Flyway 执行引擎的建表脚本（{@code flow.flyway.enabled=true}，docs/decisions/0007 第 9 节）。
 * 这是单独的一套 Flyway：脚本在 flow-core 的 {@code db/flow/mysql}，历史记在 {@code flow_schema_history}，
 * 和应用自己的 Flyway（{@code db/migration}、{@code flyway_schema_history}）互不干扰。
 */
public class FlowSchemaMigration {
    private final MigrateResult result;

    /** 构造时就执行：引擎组装之前表已经建好 */
    public FlowSchemaMigration(DataSource dataSource, FlowProperties.Flyway settings) {
        this.result = Flyway.configure()
            .dataSource(dataSource)
            .locations(settings.getLocations())
            .table(settings.getTable())
            // 库里已经有应用自己的表时也照常执行（只看这一套的历史表）
            .baselineOnMigrate(true)
            .baselineVersion("0")
            .load()
            .migrate();
    }

    /** 这次执行了几个脚本 */
    public int migrationsExecuted() { return result.migrationsExecuted; }
}
