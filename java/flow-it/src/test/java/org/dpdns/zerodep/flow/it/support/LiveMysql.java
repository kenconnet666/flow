package org.dpdns.zerodep.flow.it.support;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;

import javax.sql.DataSource;

/**
 * 实库测试连的 MySQL：连接变量是 {@code FLOW_MYSQL_URL}、{@code FLOW_MYSQL_USER}、{@code FLOW_MYSQL_PASSWORD}（库用 {@code flow_it}，
 * 见 docs/decisions/0001 的"模块划分"）。没设时跳过这些测试；设了 {@code FLOW_REQUIRE_LIVE} 时（CI）缺变量直接失败。
 * <p>每个测试前用 Flyway 清空这个库再执行 flow-core 的建表脚本：既重置数据，也验证推荐给接入方的建表方式（0007 第 9 节）。
 */
public final class LiveMysql {
    private static DataSource dataSource;

    private LiveMysql() { }

    /** 连接；没配置时跳过当前测试 */
    public static synchronized DataSource dataSource() {
        if (dataSource != null) return dataSource;
        String url = System.getenv("FLOW_MYSQL_URL");
        String user = System.getenv("FLOW_MYSQL_USER");
        String password = System.getenv("FLOW_MYSQL_PASSWORD");
        if (url == null || user == null || password == null) {
            String missing = "FLOW_MYSQL_URL/_USER/_PASSWORD not set";
            if (System.getenv("FLOW_REQUIRE_LIVE") != null) throw new IllegalStateException(missing + " but FLOW_REQUIRE_LIVE is");
            Assumptions.abort(missing + "; skipping live tests");
        }
        MysqlDataSource mysql = new MysqlDataSource();
        mysql.setURL(url);
        mysql.setUser(user);
        mysql.setPassword(password);
        dataSource = mysql;
        return dataSource;
    }

    /** 清空库，再按建表脚本建表（Flyway 的历史表叫 flow_schema_history，与接入方自己的迁移分开） */
    public static void recreateTables() {
        Flyway flyway = Flyway.configure()
            .dataSource(dataSource())
            .locations("classpath:db/flow/mysql")
            .table("flow_schema_history")
            .cleanDisabled(false)
            .load();
        flyway.clean();
        flyway.migrate();
    }
}
