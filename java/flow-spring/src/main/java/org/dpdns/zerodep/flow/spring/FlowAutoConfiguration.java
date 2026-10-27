package org.dpdns.zerodep.flow.spring;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.FlowEngineBuilder;
import org.dpdns.zerodep.flow.core.calendar.TableWorkCalendar;
import org.dpdns.zerodep.flow.core.calendar.WorkCalendar;
import org.dpdns.zerodep.flow.core.command.CommandInterceptor;
import org.dpdns.zerodep.flow.core.extension.FormHandlerFactory;
import org.dpdns.zerodep.flow.core.extension.JobHandler;
import org.dpdns.zerodep.flow.core.extension.OutcomeGuard;
import org.dpdns.zerodep.flow.core.extension.PeopleResolver;
import org.dpdns.zerodep.flow.core.extension.CountersignRule;
import org.dpdns.zerodep.flow.core.extension.CustomNode;
import org.dpdns.zerodep.flow.core.extension.FieldType;
import org.dpdns.zerodep.flow.core.extension.TodoQueryCustomizer;
import org.dpdns.zerodep.flow.core.extension.ServiceAction;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.identity.ActorProvider;
import org.dpdns.zerodep.flow.core.identity.OrgDirectory;
import org.dpdns.zerodep.flow.core.identity.PermissionPolicy;
import org.dpdns.zerodep.flow.core.listener.AfterCommit;
import org.dpdns.zerodep.flow.core.listener.FlowListener;
import org.dpdns.zerodep.flow.core.notify.NotificationChannel;
import org.dpdns.zerodep.flow.spring.http.FlowHttpController;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;
import javax.sql.DataSource;

/**
 * 引擎在 Spring Boot 里的自动配置（docs/decisions/0006 的 7.1）。应用只要：
 * <ol>
 *   <li>引入 flow-spring 与一个 MySQL 驱动，打开 easy-query 的 starter（{@code easy-query.enable=true}、{@code easy-query.database=mysql}）：
 *       引擎用它建的 {@code EasyEntityQuery} 读写，事务跟着 Spring 走；</li>
 *   <li>声明一个 {@link OrgDirectory} Bean（接自己的用户、部门、角色；引擎没有默认的组织架构）；</li>
 *   <li>当前操作人：用了 Spring Security 且登录后的 principal 实现了 {@link Actor} 时自动取；否则声明一个 {@link ActorProvider} Bean；</li>
 *   <li>建表：打开 {@code flow.flyway.enabled}，或部署时执行 flow-core 里的建表脚本。</li>
 * </ol>
 * 其余扩展点（权限、时钟、工作日历）声明了同类型的 Bean 就用它，没声明用默认的（0006 第 6 节"标准行为都能替换"）；
 * 声明的 {@link PeopleResolver}、{@link OutcomeGuard}、{@link ServiceAction}、{@link CustomNode}、{@link CountersignRule}、{@link FieldType} Bean
 * 按名字注册成解析器、守卫、动作、自定义节点、会签规则、表单字段类型，
 * {@link JobHandler} Bean 注册成作业的处理，{@link FlowListener} Bean 注册成监听（提交后的等 Spring 的事务提交了再调），{@link CommandInterceptor} Bean 按 {@code @Order} 套在命令外面。到期的作业默认由 {@link FlowJobPoller} 定时做（{@code flow.jobs.*}）。
 * 引擎注入成 {@code FlowEngine<自己的用户类型>} 即可。
 */
@AutoConfiguration(afterName = {
    // Spring Boot 3 与 4 的数据源自动配置在不同的包里，按名字写，两条线都认；easy-query 的 starter 建好 EasyEntityQuery 之后再组装
    "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
    "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
    "com.easy.query.sql.starter.EasyQueryStarterBuildAutoConfiguration"})
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
                                 ObjectProvider<FlowSchemaMigration> migration, ObjectProvider<EasyEntityQuery> easyQuery,
                                 ObjectProvider<OrgDirectory> org, ObjectProvider<ActorProvider> actors,
                                 ObjectProvider<PermissionPolicy> permissions, ObjectProvider<Clock> clock,
                                 ObjectProvider<WorkCalendar> calendar,
                                 ObjectProvider<JobHandler> jobHandlers, ObjectProvider<FlowListener> listeners, ObjectProvider<NotificationChannel> channels,
                                 ObjectProvider<AfterCommit> afterCommit, ObjectProvider<PeopleResolver> resolvers,
                                 ObjectProvider<OutcomeGuard> guards, ObjectProvider<ServiceAction> actions,
                                 ObjectProvider<CustomNode> customNodes, ObjectProvider<CountersignRule> countersignRules,
                                 ObjectProvider<FieldType> fieldTypes, ObjectProvider<TodoQueryCustomizer> todoCustomizer,
                                 ObjectProvider<CommandInterceptor> interceptors, ObjectProvider<FormHandlerFactory> formHandlerFactory) {
        migration.ifAvailable(m -> { });
        OrgDirectory directory = org.getIfAvailable();
        if (directory == null) {
            throw new IllegalStateException("the flow engine needs an OrgDirectory bean: connect it to your users, departments and roles");
        }
        EasyEntityQuery db = easyQuery.getIfUnique();
        if (db == null) {
            throw new IllegalStateException("the flow engine needs one EasyEntityQuery bean: add easy-query's Spring Boot starter and set easy-query.enable=true");
        }
        FlowEngineBuilder builder = FlowEngine.builder(db).orgDirectory(directory);
        actors.ifAvailable(builder::actorProvider);
        permissions.ifAvailable(builder::permissionPolicy);
        clock.ifUnique(builder::clock);
        // 没有自己的日历时按表里的工作日历算（管理端维护），一份都没建时周一到周五
        builder.workCalendar(calendar.getIfAvailable(() -> new TableWorkCalendar(db, ZoneId.of(properties.getCalendar().getZone()))));
        jobHandlers.orderedStream().forEach(builder::jobHandler);
        listeners.orderedStream().forEach(builder::listener);
        channels.orderedStream().forEach(builder::channel);
        interceptors.orderedStream().forEach(builder::interceptor);
        resolvers.orderedStream().forEach(builder::resolver);
        guards.orderedStream().forEach(builder::guard);
        actions.orderedStream().forEach(builder::action);
        customNodes.orderedStream().forEach(builder::customNode);
        countersignRules.orderedStream().forEach(builder::countersignRule);
        fieldTypes.orderedStream().forEach(builder::fieldType);
        todoCustomizer.ifUnique(builder::todoQueryCustomizer);
        builder.afterCommit(afterCommit.getIfAvailable(SpringAfterCommit::new));
        // 表单的后端代码按 Bean 创建：里面的 @Autowired 照常注入接入方的 Bean
        builder.formHandlerFactory(formHandlerFactory.getIfAvailable(() -> beans instanceof AutowireCapableBeanFactory autowire
            ? autowire::createBean
            : FormHandlerFactory.REFLECTION));
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

    /** 定时做到期的作业、删过期的幂等记录；{@code flow.jobs.enabled=false} 时不建，由接入方的调度平台调 {@code engine.runDueJobs()}、{@code engine.purgeRequests(...)} */
    @Bean
    @ConditionalOnProperty(prefix = "flow.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
    @SuppressWarnings("rawtypes")
    public FlowJobPoller flowJobPoller(FlowEngine engine, FlowProperties properties) {
        return new FlowJobPoller(engine, properties.getJobs().getInterval(), properties.getIdempotency().getRetention());
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
