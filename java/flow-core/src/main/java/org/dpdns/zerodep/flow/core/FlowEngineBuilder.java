package org.dpdns.zerodep.flow.core;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import org.dpdns.zerodep.flow.core.calendar.TableWorkCalendar;
import org.dpdns.zerodep.flow.core.calendar.WorkCalendar;
import org.dpdns.zerodep.flow.core.command.CommandInterceptor;
import org.dpdns.zerodep.flow.core.db.FlowEasyQuery;
import org.dpdns.zerodep.flow.core.engine.DefinitionCache;
import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.engine.EventDispatcher;
import org.dpdns.zerodep.flow.core.engine.TenantScope;
import org.dpdns.zerodep.flow.core.engine.form.FormCompiler;
import org.dpdns.zerodep.flow.core.engine.form.FormHandlers;
import org.dpdns.zerodep.flow.core.extension.Extensions;
import org.dpdns.zerodep.flow.core.extension.FormHandlerFactory;
import org.dpdns.zerodep.flow.core.extension.JobHandler;
import org.dpdns.zerodep.flow.core.extension.OutcomeGuard;
import org.dpdns.zerodep.flow.core.extension.PeopleResolver;
import org.dpdns.zerodep.flow.core.extension.ServiceAction;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.identity.ActorProvider;
import org.dpdns.zerodep.flow.core.identity.OrgDirectory;
import org.dpdns.zerodep.flow.core.identity.PermissionPolicy;
import org.dpdns.zerodep.flow.core.listener.AfterCommit;
import org.dpdns.zerodep.flow.core.listener.FlowListener;
import org.dpdns.zerodep.flow.core.notify.NotificationChannel;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 组装引擎：没给的扩展点用默认实现（0006 第 6 节）。必须给的只有 {@link #orgDirectory}；不给 {@link #actorProvider} 时，
 * 只能用带 {@code actor} 参数的方法。
 */
public class FlowEngineBuilder<U extends Actor> {
    private final EasyEntityQuery db;
    private ActorProvider<U> actors = () -> {
        throw new IllegalStateException("no ActorProvider is configured; pass the actor explicitly or set FlowEngineBuilder.actorProvider");
    };
    private OrgDirectory org;
    private PermissionPolicy<U> permissions = PermissionPolicy.allowAll();
    private Clock clock = Clock.systemUTC();
    private Supplier<Long> tenant = () -> 0L;
    /** 不给时按表里的工作日历算（管理端维护；一份都没建时周一到周五、UTC） */
    private WorkCalendar calendar;
    private final List<JobHandler> jobHandlers = new ArrayList<>();
    private final List<FlowListener> listeners = new ArrayList<>();
    private final List<NotificationChannel> channels = new ArrayList<>();
    private final List<CommandInterceptor<U>> interceptors = new ArrayList<>();
    private AfterCommit afterCommit = AfterCommit.immediately();
    private final List<PeopleResolver> resolvers = new ArrayList<>();
    private FormHandlerFactory formHandlerFactory = FormHandlerFactory.REFLECTION;
    private final List<OutcomeGuard> guards = new ArrayList<>();
    private final List<ServiceAction> actions = new ArrayList<>();

    FlowEngineBuilder(EasyEntityQuery db) { this.db = Objects.requireNonNull(db, "db"); }

    /** 当前操作人从哪来 */
    public FlowEngineBuilder<U> actorProvider(ActorProvider<U> actors) { this.actors = actors; return this; }

    /** 接入方的组织架构（必须给：引擎没有默认的组织架构） */
    public FlowEngineBuilder<U> orgDirectory(OrgDirectory org) { this.org = org; return this; }

    /** 每个操作前的权限判断；默认全部放行 */
    public FlowEngineBuilder<U> permissionPolicy(PermissionPolicy<U> permissions) { this.permissions = permissions; return this; }

    /** 时钟；默认系统时钟（UTC），测试里用固定时钟 */
    public FlowEngineBuilder<U> clock(Clock clock) { this.clock = clock; return this; }

    /** 当前租户的编号；默认不用多租户（0） */
    public FlowEngineBuilder<U> tenant(Supplier<Long> tenant) { this.tenant = tenant; return this; }

    /** 工作日历：时限写成工作日时按它算；默认周一到周五、UTC 的日期（{@link WorkCalendar#weekdays}） */
    public FlowEngineBuilder<U> workCalendar(WorkCalendar calendar) { this.calendar = calendar; return this; }

    /** 注册一个处理人解析器（定义里的 {@code { "resolver": 名字 }}）；同名的后注册的换掉先注册的 */
    public FlowEngineBuilder<U> resolver(PeopleResolver resolver) { this.resolvers.add(resolver); return this; }

    /** 注册一个提交前的守卫（处理结果的 {@code guards}） */
    public FlowEngineBuilder<U> guard(OutcomeGuard guard) { this.guards.add(guard); return this; }

    /** 注册一个自动节点的动作（{@code service} 节点的 {@code action}） */
    public FlowEngineBuilder<U> action(ServiceAction action) { this.actions.add(action); return this; }

    /** 注册一个命令拦截器：审计、业务校验、改参数、换掉某个命令的标准行为（{@link CommandInterceptor}）；先注册的在外层 */
    public FlowEngineBuilder<U> interceptor(CommandInterceptor<U> interceptor) { this.interceptors.add(interceptor); return this; }

    /** 注册一个监听：发通知、同步业务表、审计（{@link FlowListener}） */
    public FlowEngineBuilder<U> listener(FlowListener listener) { this.listeners.add(listener); return this; }

    /** 注册一个通知的渠道（站内信、邮件……，{@link NotificationChannel}）：待办到达、催办、到期、超时、抄送、被退回、流程结束时发 */
    public FlowEngineBuilder<U> channel(NotificationChannel channel) { this.channels.add(channel); return this; }

    /** 提交后的监听怎么排：默认引擎的事务做完马上调；命令会加入外面的事务时换成那个事务的提交后回调 */
    public FlowEngineBuilder<U> afterCommit(AfterCommit afterCommit) { this.afterCommit = afterCommit; return this; }

    /** 注册一种作业的处理（新的种类，或换掉内置的 timer、overdue、remind、unclaimed） */
    public FlowEngineBuilder<U> jobHandler(JobHandler handler) { this.jobHandlers.add(handler); return this; }

    /** 表单后端代码的类怎么变成对象：默认无参构造；Spring 里换成按 Bean 创建，后端代码里能 @Autowired（{@link FormHandlerFactory}） */
    public FlowEngineBuilder<U> formHandlerFactory(FormHandlerFactory factory) { this.formHandlerFactory = factory; return this; }

    /** 组装 */
    public FlowEngine<U> build() {
        if (org == null) throw new IllegalStateException("an OrgDirectory is required: the engine has no default organisation");
        // 工作进程替所有租户做作业：做每个作业时切到它的租户（TenantScope），其余时候是接入方给的当前租户
        FlowEasyQuery.install(db, TenantScope.over(tenant), clock);
        return new FlowEngine<>(new EngineContext(db, clock, org, calendar != null ? calendar : new TableWorkCalendar(db, ZoneOffset.UTC), new EventDispatcher(List.copyOf(listeners), List.copyOf(channels), afterCommit),
            new Extensions(resolvers, guards, actions), new DefinitionCache(db), new FormHandlers(db, new FormCompiler(), formHandlerFactory)), actors, permissions, List.copyOf(interceptors),
            List.copyOf(jobHandlers));
    }
}
