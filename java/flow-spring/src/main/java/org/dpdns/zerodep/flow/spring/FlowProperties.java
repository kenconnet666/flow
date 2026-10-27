package org.dpdns.zerodep.flow.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

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
 *   jobs:
 *     enabled: true                       # 定时做到期的作业：定时节点、超时、提醒（默认开）
 *     interval: 10s                       # 多久看一次
 *   calendar:
 *     zone: Asia/Shanghai                 # 工作日按哪个时区的日期算（默认 UTC）；有节假日时声明一个 WorkCalendar Bean
 * </pre>
 */
@ConfigurationProperties("flow")
public class FlowProperties {
    /** 建表（Flyway）用哪个 DataSource Bean；只有一个 DataSource 时不用写。引擎读写用 easy-query starter 的数据源 */
    private String datasource;


    /** 建表 */
    private final Flyway flyway = new Flyway();

    /** HTTP 接口 */
    private final Http http = new Http();

    /** 作业 */
    private final Jobs jobs = new Jobs();

    /** 工作日历 */
    private final Calendar calendar = new Calendar();

    /** 命令的幂等记录 */
    private final Idempotency idempotency = new Idempotency();

    /** 归档 */
    private final Archive archive = new Archive();

    public String getDatasource() { return datasource; }

    public void setDatasource(String datasource) { this.datasource = datasource; }



    public Flyway getFlyway() { return flyway; }

    public Http getHttp() { return http; }

    public Jobs getJobs() { return jobs; }

    public Calendar getCalendar() { return calendar; }

    public Idempotency getIdempotency() { return idempotency; }

    public Archive getArchive() { return archive; }

    /** 归档（0007 第 6 节）：结束超过 after 的实例搬进 *_archive，热表一直小。默认关：量不大时用不着 */
    public static class Archive {
        /** 默认关 */
        private boolean enabled;

        /** 结束多久之后归档，默认 180 天 */
        private Duration after = Duration.ofDays(180);

        public boolean isEnabled() { return enabled; }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public Duration getAfter() { return after; }

        public void setAfter(Duration after) { this.after = after; }
    }

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

    /**
     * 到期的作业（定时节点、待办的超时、提醒、无人认领）谁来做：默认本进程里定时做（{@link FlowJobPoller}）；
     * 交给已有的调度平台时关掉，由平台定时调 {@code engine.runDueJobs()}。
     */
    public static class Jobs {
        /** 默认开 */
        private boolean enabled = true;

        /** 多久看一次 */
        private Duration interval = Duration.ofSeconds(10);

        public boolean isEnabled() { return enabled; }

        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public Duration getInterval() { return interval; }

        public void setInterval(Duration interval) { this.interval = interval; }
    }

    /**
     * 命令的幂等记录（HTTP 的 {@code Idempotency-Key}，{@code flow_request}）留多久：早于这个时长的由 {@link FlowJobPoller} 每小时删一次。
     * 调用方的重试都要在这个窗口里；默认 24 小时（同 Stripe 的做法）。
     */
    public static class Idempotency {
        /** 默认 24 小时 */
        private Duration retention = Duration.ofHours(24);

        public Duration getRetention() { return retention; }

        public void setRetention(Duration retention) { this.retention = retention; }
    }

    /** 默认的工作日历（周一到周五）按哪个时区的日期算；有节假日、调休时声明一个 {@code WorkCalendar} Bean 换掉 */
    public static class Calendar {
        /** 默认 UTC */
        private String zone = "UTC";

        public String getZone() { return zone; }

        public void setZone(String zone) { this.zone = zone; }
    }
}
