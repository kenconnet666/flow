package org.dpdns.zerodep.flow.core;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import org.dpdns.zerodep.flow.core.db.FlowEasyQuery;
import org.dpdns.zerodep.flow.core.engine.DefinitionCache;
import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.engine.EventDispatcher;
import org.dpdns.zerodep.flow.core.engine.Extensions;
import org.dpdns.zerodep.flow.core.engine.TableWorkCalendar;
import org.dpdns.zerodep.flow.core.engine.TenantScope;
import org.dpdns.zerodep.flow.core.engine.form.FormCompiler;
import org.dpdns.zerodep.flow.core.engine.form.FormHandlers;
import org.dpdns.zerodep.flow.core.spi.Actor;
import org.dpdns.zerodep.flow.core.spi.AfterCommit;
import org.dpdns.zerodep.flow.core.spi.CommandInterceptor;
import org.dpdns.zerodep.flow.core.spi.ComputedValue;
import org.dpdns.zerodep.flow.core.spi.CountersignRule;
import org.dpdns.zerodep.flow.core.spi.CustomNode;
import org.dpdns.zerodep.flow.core.spi.ExtensionParam;
import org.dpdns.zerodep.flow.core.spi.FieldType;
import org.dpdns.zerodep.flow.core.spi.FlowListener;
import org.dpdns.zerodep.flow.core.spi.FormHandlerFactory;
import org.dpdns.zerodep.flow.core.spi.JobHandler;
import org.dpdns.zerodep.flow.core.spi.NotificationChannel;
import org.dpdns.zerodep.flow.core.spi.OrgDirectory;
import org.dpdns.zerodep.flow.core.spi.OutcomeGuard;
import org.dpdns.zerodep.flow.core.spi.PeopleResolver;
import org.dpdns.zerodep.flow.core.spi.PermissionPolicy;
import org.dpdns.zerodep.flow.core.spi.ServiceAction;
import org.dpdns.zerodep.flow.core.spi.TodoQueryCustomizer;
import org.dpdns.zerodep.flow.core.spi.ViewPolicy;
import org.dpdns.zerodep.flow.core.spi.WorkCalendar;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 组装引擎：没给的扩展点用默认实现（0006 第 6 节）。必须给的只有 {@link #orgDirectory}。
 * 当前操作人不归引擎管：每个方法由调用方传入（HTTP 接口用 {@code ActorProvider} 取登录的人）。
 */
public class FlowEngineBuilder<U extends Actor> {
    private final EasyEntityQuery db;
    private OrgDirectory org;
    private PermissionPolicy<U> permissions = PermissionPolicy.allowAll();
    /** 不设时是 ViewPolicy.participantsOr(权限)：组装时按最后设的权限算 */
    private ViewPolicy<U> viewPolicy;
    private Clock clock = Clock.systemUTC();
    private Supplier<Long> tenant = () -> 0L;
    /** 不给时按表里的工作日历算（管理端维护；一份都没建时周一到周五、UTC） */
    private WorkCalendar calendar;
    private final List<JobHandler> jobHandlers = new ArrayList<>();
    private final List<FlowListener> listeners = new ArrayList<>();
    private final List<NotificationChannel> channels = new ArrayList<>();
    private final List<CommandInterceptor<U>> interceptors = new ArrayList<>();
    private AfterCommit afterCommit = AfterCommit.immediately();
    private FormHandlerFactory formHandlerFactory = FormHandlerFactory.REFLECTION;
    /** 按名字注册的扩展（解析器、守卫、动作、计算值、自定义节点、会签规则、字段类型）与节点业务信息的字段 */
    private final Extensions.Builder extensions = Extensions.builder();
    private boolean formServerCode = true;
    private TodoQueryCustomizer todoCustomizer = TodoQueryCustomizer.NONE;

    FlowEngineBuilder(EasyEntityQuery db) { this.db = Objects.requireNonNull(db, "db"); }

    /** 接入方的组织架构（必须给：引擎没有默认的组织架构） */
    public FlowEngineBuilder<U> orgDirectory(OrgDirectory org) { this.org = org; return this; }

    /** 每个操作前的权限判断；默认全部放行 */
    public FlowEngineBuilder<U> permissionPolicy(PermissionPolicy<U> permissions) { this.permissions = permissions; return this; }

    /** 谁能看实例；默认参与过的人，加上有 instances.search 权限的人 */
    public FlowEngineBuilder<U> viewPolicy(ViewPolicy<U> viewPolicy) { this.viewPolicy = viewPolicy; return this; }

    /** 时钟；默认系统时钟（UTC），测试里用固定时钟 */
    public FlowEngineBuilder<U> clock(Clock clock) { this.clock = clock; return this; }

    /** 当前租户的编号；默认不用多租户（0） */
    public FlowEngineBuilder<U> tenant(Supplier<Long> tenant) { this.tenant = tenant; return this; }

    /** 工作日历：时限写成工作日时按它算；默认周一到周五、UTC 的日期（{@link WorkCalendar#weekdays}） */
    public FlowEngineBuilder<U> workCalendar(WorkCalendar calendar) { this.calendar = calendar; return this; }

    /** 注册一个处理人解析器（定义里的 {@code { "resolver": 名字 }}）；同名的后注册的换掉先注册的 */
    public FlowEngineBuilder<U> resolver(PeopleResolver resolver) { extensions.resolver(resolver); return this; }

    /** 注册一个提交前的守卫（处理结果的 {@code guards}） */
    public FlowEngineBuilder<U> guard(OutcomeGuard guard) { extensions.guard(guard); return this; }

    /** 注册一个自动节点的动作（{@code service} 节点的 {@code action}） */
    public FlowEngineBuilder<U> action(ServiceAction action) { extensions.action(action); return this; }

    /** 注册一种自定义节点（定义里 {@code "type": "x-名字"}，{@link CustomNode}） */
    public FlowEngineBuilder<U> customNode(CustomNode node) { extensions.node(node); return this; }

    /** 注册一个会签的完成规则（{@code multi.complete} 写的名字，{@link CountersignRule}） */
    public FlowEngineBuilder<U> countersignRule(CountersignRule rule) { extensions.countersignRule(rule); return this; }

    /** 注册一种表单字段类型（清单里 {@code "type": 名字}，{@link FieldType}） */
    public FlowEngineBuilder<U> fieldType(FieldType type) { extensions.fieldType(type); return this; }

    /** 注册一个计算值（条件里的 {@code fn.名字}，{@link ComputedValue}） */
    public FlowEngineBuilder<U> computed(ComputedValue value) { extensions.computed(value); return this; }

    /** 节点的业务信息加一个字段：设计器的节点面板按它画控件，填的值写进节点的 {@code meta}（引擎原样保存，接入方的监听读它） */
    public FlowEngineBuilder<U> nodeMetaField(ExtensionParam field) { extensions.nodeMetaField(field); return this; }

    /** 允不允许在线写表单的后端代码（Java，发布时编译、运行时执行）；默认允许，生产环境不想让设计权限等于服务器上跑代码时关掉 */
    public FlowEngineBuilder<U> formServerCode(boolean enabled) { this.formServerCode = enabled; return this; }

    /** 待办列表连接入方自己的表：加条件、补业务列（{@link TodoQueryCustomizer}）；默认不连 */
    public FlowEngineBuilder<U> todoQueryCustomizer(TodoQueryCustomizer customizer) { this.todoCustomizer = customizer; return this; }

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
            extensions.build(), todoCustomizer, new DefinitionCache(db), new FormHandlers(db, new FormCompiler(), formHandlerFactory, formServerCode)), permissions,
            viewPolicy != null ? viewPolicy : ViewPolicy.participantsOr(permissions), List.copyOf(interceptors),
            List.copyOf(jobHandlers));
    }
}
