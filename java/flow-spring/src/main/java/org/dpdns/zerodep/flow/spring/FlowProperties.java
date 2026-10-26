package org.dpdns.zerodep.flow.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 引擎在 Spring Boot 里的配置（{@code application.yml} 的 {@code flow.*}）：
 * <pre>
 * flow:
 *   datasource: flowDataSource            # 多数据源时：引擎用哪个 DataSource Bean（只有一个时不用写）
 *   transaction-manager: flowTxManager    # 多数据源时：那个数据源的事务管理器 Bean
 *   flyway:
 *     enabled: true                       # 启动时执行引擎的建表脚本（单独一套 Flyway，默认关）
 *   http:
 *     enabled: true                       # 打开引擎自带的 HTTP 接口（默认关）
 *     path: /flow                         # 接口的路径前缀
 * </pre>
 */
@ConfigurationProperties("flow")
public class FlowProperties {
    /** 引擎用哪个 DataSource Bean；只有一个 DataSource 时不用写 */
    private String datasource;

    /** 引擎的事务交给哪个事务管理器 Bean；只有一个时不用写 */
    private String transactionManager;

    /** 建表 */
    private final Flyway flyway = new Flyway();

    /** HTTP 接口 */
    private final Http http = new Http();

    public String getDatasource() { return datasource; }

    public void setDatasource(String datasource) { this.datasource = datasource; }

    public String getTransactionManager() { return transactionManager; }

    public void setTransactionManager(String transactionManager) { this.transactionManager = transactionManager; }

    public Flyway getFlyway() { return flyway; }

    public Http getHttp() { return http; }

    /**
     * 启动时用 Flyway 执行引擎的建表脚本（docs/decisions/0007 第 9 节）。和应用自己的 Flyway 分开：自己的脚本位置、自己的历史表。
     * 不打开时由部署脚本或 DBA 执行 flow-core 里的 {@code db/flow/mysql/*.sql}。
     */
    public static class Flyway {
        /** 默认关 */
        private boolean enabled;

        /** 脚本的位置 */
        private String locations = "classpath:db/flow/mysql";

        /** Flyway 的历史表 */
        private String table = "flow_schema_history";

        public boolean isEnabled() { return enabled; }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getLocations() { return locations; }

        public void setLocations(String locations) { this.locations = locations; }

        public String getTable() { return table; }

        public void setTable(String table) { this.table = table; }
    }

    /**
     * 引擎自带的 HTTP 接口（docs/decisions/0006 的 7.3）：每个命令一个 POST，查询用 GET；HTTP 一律 200，响应体带业务错误码（0008）。
     * 登录与权限由应用自己的框架管（Spring Security 等），引擎只从当前操作人取人。
     */
    public static class Http {
        /** 默认关 */
        private boolean enabled;

        /** 路径前缀 */
        private String path = "/flow";

        public boolean isEnabled() { return enabled; }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getPath() { return path; }

        public void setPath(String path) { this.path = path; }
    }
}
