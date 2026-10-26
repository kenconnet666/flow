package org.dpdns.zerodep.flow.spring;

import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.calendar.WorkCalendar;
import org.dpdns.zerodep.flow.core.engine.JobHandler;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.identity.ActorProvider;
import org.dpdns.zerodep.flow.core.identity.OrgDirectory;
import org.dpdns.zerodep.flow.core.identity.PermissionPolicy;
import org.dpdns.zerodep.flow.core.listener.AfterCommit;
import org.dpdns.zerodep.flow.core.listener.FlowListener;
import org.dpdns.zerodep.flow.core.store.CcStore;
import org.dpdns.zerodep.flow.core.store.DefinitionStore;
import org.dpdns.zerodep.flow.core.store.EventStore;
import org.dpdns.zerodep.flow.core.store.FormDataStore;
import org.dpdns.zerodep.flow.core.store.FormStore;
import org.dpdns.zerodep.flow.core.store.InstanceStore;
import org.dpdns.zerodep.flow.core.store.JobStore;
import org.dpdns.zerodep.flow.core.store.WorkItemStore;
import org.dpdns.zerodep.flow.spring.http.FlowHttpController;
import org.dpdns.zerodep.query.spring.SpringConnections;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.ZoneId;

/**
 * 引擎在 Spring Boot 里的自动配置（docs/decisions/0006 的 7.1）。应用只要：
 * <ol>
 *   <li>引入 flow-spring 与一个 MySQL 驱动；</li>
 *   <li>声明一个 {@link OrgDirectory} Bean（接自己的用户、部门、角色；引擎没有默认的组织架构）；</li>
 *   <li>当前操作人：用了 Spring Security 且登录后的 principal 实现了 {@link Actor} 时自动取；否则声明一个 {@link ActorProvider} Bean；</li>
 *   <li>建表：打开 {@code flow.flyway.enabled}，或部署时执行 flow-core 里的建表脚本。</li>
 * </ol>
 * 其余扩展点（权限、时钟、工作日历、八个存储）声明了同类型的 Bean 就用它，没声明用默认的（0006 第 6 节"标准行为都能替换"）；
 * 声明的 {@link JobHandler} Bean 注册成作业的处理，{@link FlowListener} Bean 注册成监听（提交后的等 Spring 的事务提交了再调）。到期的作业默认由 {@link FlowJobPoller} 定时做（{@code flow.jobs.*}）。
 * 引擎注入成 {@code FlowEngine<自己的用户类型>} 即可。
 */
@AutoConfiguration(afterName = {
    // Spring Boot 3 与 4 的数据源、事务管理器自动配置在不同的包里，按名字写，两条线都认
    "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
    "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
    "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
    "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration"})
@ConditionalOnClass(FlowEngine.class)
@EnableConfigurationProperties(FlowProperties.class)
public class FlowAutoConfiguration {
    /**
     * 引擎。返回类型不带泛型参数，所以应用可以注入成 {@code FlowEngine<MyUser>}。
     * 打开了 Flyway 建表时，先建好表再组装。
     */
    @Bean
    @ConditionalOnMissingBean
    @SuppressWarnings({"rawtypes", "unchecked"})
    public FlowEngine flowEngine(BeanFactory beans, FlowProperties properties,
                                 ObjectProvider<FlowSchemaMigration> migration,
                                 ObjectProvider<OrgDirectory> org, ObjectProvider<ActorProvider> actors,
                                 ObjectProvider<PermissionPolicy> permissions, ObjectProvider<Clock> clock,
                                 ObjectProvider<DefinitionStore> definitionStore, ObjectProvider<FormStore> formStore,
                                 ObjectProvider<FormDataStore> formDataStore, ObjectProvider<InstanceStore> instanceStore,
                                 ObjectProvider<WorkItemStore> workItemStore, ObjectProvider<EventStore> eventStore,
                                 ObjectProvider<JobStore> jobStore, ObjectProvider<CcStore> ccStore, ObjectProvider<WorkCalendar> calendar,
                                 ObjectProvider<JobHandler> jobHandlers, ObjectProvider<FlowListener> listeners,
                                 ObjectProvider<AfterCommit> afterCommit) {
        migration.ifAvailable(m -> { });
        OrgDirectory directory = org.getIfAvailable();
        if (directory == null) {
            throw new IllegalStateException("the flow engine needs an OrgDirectory bean: connect it to your users, departments and roles");
        }
        // 引擎的连接：选定的数据源，事务交给它的事务管理器（外面已有事务时加入）
        DataSource dataSource = pick(beans, DataSource.class, properties.getDatasource(), "flow.datasource");
        PlatformTransactionManager transactions = pick(beans, PlatformTransactionManager.class, properties.getTransactionManager(),
            "flow.transaction-manager");
        FlowEngine.Builder builder = FlowEngine.builder(SpringConnections.of(dataSource, transactions)).orgDirectory(directory);
        actors.ifAvailable(builder::actorProvider);
        permissions.ifAvailable(builder::permissionPolicy);
        clock.ifUnique(builder::clock);
        definitionStore.ifAvailable(builder::definitionStore);
        formStore.ifAvailable(builder::formStore);
        formDataStore.ifAvailable(builder::formDataStore);
        instanceStore.ifAvailable(builder::instanceStore);
        workItemStore.ifAvailable(builder::workItemStore);
        eventStore.ifAvailable(builder::eventStore);
        jobStore.ifAvailable(builder::jobStore);
        ccStore.ifAvailable(builder::ccStore);
        builder.workCalendar(calendar.getIfAvailable(() -> WorkCalendar.weekdays(ZoneId.of(properties.getCalendar().getZone()))));
        jobHandlers.orderedStream().forEach(builder::jobHandler);
        listeners.orderedStream().forEach(builder::listener);
        builder.afterCommit(afterCommit.getIfAvailable(SpringAfterCommit::new));
        return builder.build();
    }

    /** 打开 {@code flow.flyway.enabled} 时，启动时执行引擎的建表脚本 */
    @Bean
    @ConditionalOnClass(name = "org.flywaydb.core.Flyway")
    @ConditionalOnProperty(prefix = "flow.flyway", name = "enabled", havingValue = "true")
    public FlowSchemaMigration flowSchemaMigration(FlowProperties properties, BeanFactory beans) {
        DataSource dataSource = pick(beans, DataSource.class, properties.getDatasource(), "flow.datasource");
        return new FlowSchemaMigration(dataSource, properties.getFlyway());
    }

    /** 定时做到期的作业；{@code flow.jobs.enabled=false} 时不建，由接入方的调度平台调 {@code engine.runDueJobs()} */
    @Bean
    @ConditionalOnProperty(prefix = "flow.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
    @SuppressWarnings("rawtypes")
    public FlowJobPoller flowJobPoller(FlowEngine engine, FlowProperties properties) {
        return new FlowJobPoller(engine, properties.getJobs().getInterval());
    }

    /** 用了 Spring Security、应用没给 {@link ActorProvider} 时：当前操作人取登录的 principal */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.security.core.context.SecurityContextHolder")
    static class SecurityActors {
        @Bean
        @ConditionalOnMissingBean(ActorProvider.class)
        ActorProvider<Actor> flowActorProvider() { return new SecurityContextActorProvider(); }
    }

    /** 打开 {@code flow.http.enabled}、应用是 Spring MVC 时：引擎自带的 HTTP 接口 */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
    @ConditionalOnProperty(prefix = "flow.http", name = "enabled", havingValue = "true")
    static class Http {
        @Bean
        @SuppressWarnings("rawtypes")
        FlowHttpController flowHttpController(FlowEngine engine) { return new FlowHttpController(engine); }
    }

    /** 按名字取 Bean；没写名字时要求只有一个，有几个时报错并说明用哪个配置项指定 */
    private static <T> T pick(BeanFactory beans, Class<T> type, String name, String property) {
        if (name != null && !name.isBlank()) return beans.getBean(name, type);
        T unique = beans.getBeanProvider(type).getIfUnique();
        if (unique == null) {
            throw new IllegalStateException("the flow engine needs exactly one " + type.getSimpleName() + " bean, or set " + property);
        }
        return unique;
    }
}
