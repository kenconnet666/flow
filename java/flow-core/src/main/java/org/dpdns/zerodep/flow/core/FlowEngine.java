package org.dpdns.zerodep.flow.core;

import org.dpdns.zerodep.flow.core.command.AddSignersCommand;
import org.dpdns.zerodep.flow.core.command.CommentCommand;
import org.dpdns.zerodep.flow.core.command.DelegateCommand;
import org.dpdns.zerodep.flow.core.command.JumpCommand;
import org.dpdns.zerodep.flow.core.command.ReassignCommand;
import org.dpdns.zerodep.flow.core.command.ReadCcCommand;
import org.dpdns.zerodep.flow.core.command.ReleaseCommand;
import org.dpdns.zerodep.flow.core.command.RemindCommand;
import org.dpdns.zerodep.flow.core.command.RemoveSignersCommand;
import org.dpdns.zerodep.flow.core.command.ResumeCommand;
import org.dpdns.zerodep.flow.core.command.RetryJobCommand;
import org.dpdns.zerodep.flow.core.command.SuspendCommand;
import org.dpdns.zerodep.flow.core.command.TerminateCommand;
import org.dpdns.zerodep.flow.core.command.TransferCommand;
import org.dpdns.zerodep.flow.core.command.CcCommand;
import org.dpdns.zerodep.flow.core.command.ClaimCommand;
import org.dpdns.zerodep.flow.core.command.CommandResult;
import org.dpdns.zerodep.flow.core.command.CompleteCommand;
import org.dpdns.zerodep.flow.core.command.FormPublication;
import org.dpdns.zerodep.flow.core.command.StartCommand;
import org.dpdns.zerodep.flow.core.command.WithdrawCommand;
import org.dpdns.zerodep.flow.core.calendar.WorkCalendar;
import org.dpdns.zerodep.flow.core.engine.AdminHandler;
import org.dpdns.zerodep.flow.core.engine.BuiltinJobs;
import org.dpdns.zerodep.flow.core.engine.ClaimHandler;
import org.dpdns.zerodep.flow.core.engine.CollaborationHandler;
import org.dpdns.zerodep.flow.core.engine.CompleteHandler;
import org.dpdns.zerodep.flow.core.engine.DefinitionCache;
import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.engine.EventDispatcher;
import org.dpdns.zerodep.flow.core.engine.JobHandler;
import org.dpdns.zerodep.flow.core.engine.JobRunner;
import org.dpdns.zerodep.flow.core.engine.Publishing;
import org.dpdns.zerodep.flow.core.engine.Queries;
import org.dpdns.zerodep.flow.core.engine.StartHandler;
import org.dpdns.zerodep.flow.core.engine.TenantScope;
import org.dpdns.zerodep.flow.core.engine.WithdrawHandler;
import org.dpdns.zerodep.flow.core.extension.Extensions;
import org.dpdns.zerodep.flow.core.extension.OutcomeGuard;
import org.dpdns.zerodep.flow.core.extension.PeopleResolver;
import org.dpdns.zerodep.flow.core.extension.ServiceAction;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.identity.ActorProvider;
import org.dpdns.zerodep.flow.core.identity.OrgDirectory;
import org.dpdns.zerodep.flow.core.identity.PermissionPolicy;
import org.dpdns.zerodep.flow.core.listener.AfterCommit;
import org.dpdns.zerodep.flow.core.listener.FlowListener;
import org.dpdns.zerodep.flow.core.store.CcStore;
import org.dpdns.zerodep.flow.core.store.DefinitionStore;
import org.dpdns.zerodep.flow.core.store.DefinitionStore.StoredDefinition;
import org.dpdns.zerodep.flow.core.store.EventStore;
import org.dpdns.zerodep.flow.core.store.FormDataStore;
import org.dpdns.zerodep.flow.core.store.FormStore;
import org.dpdns.zerodep.flow.core.store.FormStore.StoredForm;
import org.dpdns.zerodep.flow.core.store.InstanceStore;
import org.dpdns.zerodep.flow.core.store.JobStore;
import org.dpdns.zerodep.flow.core.store.Stores;
import org.dpdns.zerodep.flow.core.store.WorkItemStore;
import org.dpdns.zerodep.flow.core.store.query.FlowDatabase;
import org.dpdns.zerodep.flow.core.store.query.QueryStores;
import org.dpdns.zerodep.flow.core.view.InstanceDetail;
import org.dpdns.zerodep.flow.core.view.StartView;
import org.dpdns.zerodep.flow.core.view.StartableProcess;
import org.dpdns.zerodep.flow.core.view.WorkItemView;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;
import org.dpdns.zerodep.flow.model.runtime.CcItem;
import org.dpdns.zerodep.flow.model.runtime.ProcessInstance;
import org.dpdns.zerodep.flow.model.runtime.TodoItem;
import org.dpdns.zerodep.query.exec.ConnectionProvider;
import org.dpdns.zerodep.query.exec.Database;
import org.dpdns.zerodep.query.exec.Page;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 流程引擎的门面（docs/decisions/0006）：接入方只和它打交道。命令改状态，查询读，扩展点由 {@link Builder} 给出。不依赖 Spring。
 * <pre>{@code
 * FlowEngine<MyUser> engine = FlowEngine.<MyUser>builder(dataSource).actorProvider(security::currentUser).orgDirectory(org).build();
 * engine.publishForm(new FormPublication("purchase", "采购申请", null, manifestJson, null, null, null, null, null, null));
 * engine.publishDefinition(definitionJson, "第一版");
 * CommandResult started = engine.start(StartCommand.of("purchase", Map.of("amount", "1200.00")));
 * engine.complete(CompleteCommand.of(workItemId, "approve"));
 * Page<TodoItem> todo = engine.todo(1, 20);
 * }</pre>
 * <ul>
 *   <li>表由接入方建：执行 flow-core 里的 {@code db/flow/mysql/*.sql}，推荐交给 Flyway（docs/decisions/0007 第 9 节）；</li>
 *   <li>每个命令在一个事务里执行（外面已有同一个数据源的事务时加入），处理的步骤见各处理类（{@code engine} 包）；</li>
 *   <li>出错时抛带业务错误码的 {@link FlowException}（0008），HTTP 接口写成响应体；</li>
 *   <li>不带操作人的方法取 {@link ActorProvider} 的当前操作人，带 {@code actor} 的给后台任务、系统操作用；每个操作先问 {@link PermissionPolicy}。</li>
 * </ul>
 */
public final class FlowEngine<U extends Actor> {
    private final ActorProvider<U> actors;
    private final PermissionPolicy<U> permissions;
    private final StartHandler starts;
    private final ClaimHandler claims;
    private final CompleteHandler completions;
    private final WithdrawHandler withdrawals;
    private final CollaborationHandler collaboration;
    private final AdminHandler admin;
    private final Publishing publishing;
    private final Queries queries;
    private final JobRunner jobs;

    private FlowEngine(EngineContext context, ActorProvider<U> actors, PermissionPolicy<U> permissions, List<JobHandler> jobHandlers) {
        this.actors = actors;
        this.permissions = permissions;
        DefinitionCache definitions = new DefinitionCache(context);
        this.starts = new StartHandler(context, definitions);
        this.claims = new ClaimHandler(context, definitions);
        this.completions = new CompleteHandler(context, definitions, claims);
        this.withdrawals = new WithdrawHandler(context, definitions);
        this.collaboration = new CollaborationHandler(context, definitions, claims);
        this.admin = new AdminHandler(context, definitions);
        this.publishing = new Publishing(context);
        this.queries = new Queries(context, definitions);
        // 内置的作业在前，接入方注册的在后：同一种时接入方的换掉内置的
        List<JobHandler> handlers = new ArrayList<>(new BuiltinJobs(context, definitions, completions).handlers());
        handlers.addAll(jobHandlers);
        this.jobs = new JobRunner(context, handlers);
    }

    /** 开始组装：引擎在这个数据源上建自己的数据库实例（方言由连接决定；不在事务里时每条语句借一个连接） */
    public static <U extends Actor> Builder<U> builder(DataSource dataSource) { return new Builder<>(ConnectionProvider.of(dataSource)); }

    /** 开始组装：连接由接入方给，例如 Spring 里带事务管理器的 {@code SpringConnections.of(dataSource, transactionManager)} */
    public static <U extends Actor> Builder<U> builder(ConnectionProvider connections) { return new Builder<>(connections); }

    // ───────── 发布 ─────────

    /** 发布流程定义（当前操作人），见 {@link #publishDefinition(Actor, String, String)} */
    public StoredDefinition publishDefinition(String json, String note) { return publishDefinition(actors.current(), json, note); }

    /** 发布流程定义：校验不通过时报 {@code flow.definition-invalid}，错误明细里是问题列表；版本号由后端给 */
    public StoredDefinition publishDefinition(U actor, String json, String note) {
        allow(actor, "definitions.publish", null);
        return publishing.publishDefinition(actor, json, note);
    }

    /** 发布表单（当前操作人） */
    public StoredForm publishForm(FormPublication form) { return publishForm(actors.current(), form); }

    /** 发布表单：字段清单校验不通过时报 {@code flow.validation-failed} */
    public StoredForm publishForm(U actor, FormPublication form) {
        allow(actor, "forms.publish", form.key());
        return publishing.publishForm(actor, form);
    }

    // ───────── 命令 ─────────

    /** 发起（当前操作人） */
    public CommandResult start(StartCommand command) { return start(actors.current(), command); }

    /** 发起：按最新发布的版本走（步骤见 {@link StartHandler}） */
    public CommandResult start(U actor, StartCommand command) {
        allow(actor, "start", command.key());
        return starts.handle(actor, command);
    }

    /** 认领（当前操作人） */
    public CommandResult claim(ClaimCommand command) { return claim(actors.current(), command); }

    /** 认领收件箱里的待办：先到先得（见 {@link ClaimHandler}） */
    public CommandResult claim(U actor, ClaimCommand command) {
        allow(actor, "claim", String.valueOf(command.workItemId()));
        return claims.handle(actor, command);
    }

    /** 处理（当前操作人） */
    public CommandResult complete(CompleteCommand command) { return complete(actors.current(), command); }

    /** 处理一个待办（步骤见 {@link CompleteHandler}） */
    public CommandResult complete(U actor, CompleteCommand command) {
        allow(actor, "complete", String.valueOf(command.workItemId()));
        return completions.handle(actor, command);
    }

    /** 撤回（当前操作人） */
    public CommandResult withdraw(WithdrawCommand command) { return withdraw(actors.current(), command); }

    /** 发起人撤回整个实例（规则见 {@link WithdrawHandler}） */
    public CommandResult withdraw(U actor, WithdrawCommand command) {
        allow(actor, "withdraw", String.valueOf(command.instanceId()));
        return withdrawals.handle(actor, command);
    }

    // ───────── 协作与管理员命令（0006 的 3.1–3.4） ─────────

    /** 转办（当前操作人） */
    public CommandResult transfer(TransferCommand command) { return transfer(actors.current(), command); }

    /** 转办：交给别人，自己不再参与 */
    public CommandResult transfer(U actor, TransferCommand command) {
        allow(actor, "transfer", String.valueOf(command.workItemId()));
        return collaboration.transfer(actor, command);
    }

    /** 委派（当前操作人） */
    public CommandResult delegate(DelegateCommand command) { return delegate(actors.current(), command); }

    /** 委派：请别人先办，办完回到自己手里 */
    public CommandResult delegate(U actor, DelegateCommand command) {
        allow(actor, "delegate", String.valueOf(command.workItemId()));
        return collaboration.delegate(actor, command);
    }

    /** 加签（当前操作人） */
    public CommandResult addSigners(AddSignersCommand command) { return addSigners(actors.current(), command); }

    /** 加签：前加签、后加签、并加 */
    public CommandResult addSigners(U actor, AddSignersCommand command) {
        allow(actor, "addSigners", String.valueOf(command.workItemId()));
        return collaboration.addSigners(actor, command);
    }

    /** 减签（当前操作人） */
    public CommandResult removeSigners(RemoveSignersCommand command) { return removeSigners(actors.current(), command); }

    /** 减签：取消自己加的、还没办的人 */
    public CommandResult removeSigners(U actor, RemoveSignersCommand command) {
        allow(actor, "removeSigners", String.valueOf(command.workItemId()));
        return collaboration.removeSigners(actor, command);
    }

    /** 退回收件箱（当前操作人） */
    public CommandResult release(ReleaseCommand command) { return release(actors.current(), command); }

    /** 退回收件箱：自己以后不能再认领这一条 */
    public CommandResult release(U actor, ReleaseCommand command) {
        allow(actor, "release", String.valueOf(command.workItemId()));
        return collaboration.release(actor, command);
    }

    /** 催办（当前操作人） */
    public CommandResult remind(RemindCommand command) { return remind(actors.current(), command); }

    /** 催办：同一个待办一小时内只记一次 */
    public CommandResult remind(U actor, RemindCommand command) {
        allow(actor, "remind", String.valueOf(command.instanceId()));
        return collaboration.remind(actor, command);
    }

    /** 留言（当前操作人） */
    public CommandResult comment(CommentCommand command) { return comment(actors.current(), command); }

    /** 留言：记进流转记录 */
    public CommandResult comment(U actor, CommentCommand command) {
        allow(actor, "comment", String.valueOf(command.instanceId()));
        return collaboration.comment(actor, command);
    }

    /** 抄送（当前操作人） */
    public CommandResult cc(CcCommand command) { return cc(actors.current(), command); }

    /** 抄送（知会）：每人一条抄送，记进流转记录 */
    public CommandResult cc(U actor, CcCommand command) {
        allow(actor, "cc", String.valueOf(command.instanceId()));
        return collaboration.cc(actor, command);
    }

    /** 读了抄送（当前操作人） */
    public int readCc(ReadCcCommand command) { return readCc(actors.current(), command); }

    /** 读了抄送：一个实例抄送给我的都标成已读，返回标了几条 */
    public int readCc(U actor, ReadCcCommand command) {
        allow(actor, "readCc", String.valueOf(command.instanceId()));
        return collaboration.readCc(actor, command);
    }

    /** 终止（管理员）（当前操作人） */
    public CommandResult terminate(TerminateCommand command) { return terminate(actors.current(), command); }

    /** 终止（管理员）：打开着的待办都取消，实例结束 */
    public CommandResult terminate(U actor, TerminateCommand command) {
        allow(actor, "terminate", String.valueOf(command.instanceId()));
        return admin.terminate(actor, command);
    }

    /** 暂停（管理员）（当前操作人） */
    public CommandResult suspend(SuspendCommand command) { return suspend(actors.current(), command); }

    /** 暂停（管理员）：待办都不能处理，直到恢复 */
    public CommandResult suspend(U actor, SuspendCommand command) {
        allow(actor, "suspend", String.valueOf(command.instanceId()));
        return admin.suspend(actor, command);
    }

    /** 恢复（管理员）（当前操作人） */
    public CommandResult resume(ResumeCommand command) { return resume(actors.current(), command); }

    /** 恢复（管理员） */
    public CommandResult resume(U actor, ResumeCommand command) {
        allow(actor, "resume", String.valueOf(command.instanceId()));
        return admin.resume(actor, command);
    }

    /** 跳转（管理员）（当前操作人） */
    public CommandResult jump(JumpCommand command) { return jump(actors.current(), command); }

    /** 跳转（管理员）：原来的待办取消，从指定的节点重新走 */
    public CommandResult jump(U actor, JumpCommand command) {
        allow(actor, "jump", String.valueOf(command.instanceId()));
        return admin.jump(actor, command);
    }

    /** 改派（管理员、收件箱负责人）（当前操作人） */
    public CommandResult reassign(ReassignCommand command) { return reassign(actors.current(), command); }

    /** 改派（管理员、收件箱负责人）：待办直接落到这个人 */
    public CommandResult reassign(U actor, ReassignCommand command) {
        allow(actor, "reassign", String.valueOf(command.workItemId()));
        return admin.reassign(actor, command);
    }

    /** 失败的作业重新来过（管理员）（当前操作人） */
    public boolean retryJob(RetryJobCommand command) { return retryJob(actors.current(), command); }

    /** 失败的作业重新来过（管理员）：修好外部系统后让异步的自动节点再做一次 */
    public boolean retryJob(U actor, RetryJobCommand command) {
        allow(actor, "retryJob", String.valueOf(command.jobId()));
        return admin.retryJob(actor, command);
    }

    // ───────── 作业 ─────────

    /**
     * 做一批到期的作业（定时节点到点、待办超时、提醒、无人认领），返回做了几个。Spring Boot 里默认定时调它（{@code flow.jobs.*}）；
     * 交给已有的调度平台时关掉默认的、由平台调它。几个进程同时调不会重复做（{@link JobRunner}）。
     */
    public int runDueJobs() { return jobs.runDue(); }

    // ───────── 查询 ─────────

    /** 我能发起的流程（当前操作人） */
    public List<StartableProcess> startable() { return startable(actors.current()); }

    /** 我能发起的流程：启用着、发起人名单里有我，按分类、名称排 */
    public List<StartableProcess> startable(U actor) {
        allow(actor, "startable", null);
        return queries.startable(actor);
    }

    /** 发起页（当前操作人） */
    public StartView startView(String key) { return startView(actors.current(), key); }

    /** 发起页：流程与开始节点上的表单 */
    public StartView startView(U actor, String key) {
        allow(actor, "startView", key);
        return queries.startView(actor, key);
    }

    /** 待办详情（当前操作人） */
    public WorkItemView workItem(Long workItemId) { return workItem(actors.current(), workItemId); }

    /** 待办详情：待办与实例、这个节点上的表单、能选的处理结果、我能不能认领与处理 */
    public WorkItemView workItem(U actor, Long workItemId) {
        allow(actor, "workItem", String.valueOf(workItemId));
        return queries.workItem(actor, workItemId);
    }

    /** 我的待办（当前操作人） */
    public Page<TodoItem> todo(int page, int size) { return todo(actors.current(), page, size); }

    /** 我的待办：落到我的，加上我能认领的；按到达时间从新到旧，带实例的标题与摘要列 */
    public Page<TodoItem> todo(U actor, int page, int size) {
        allow(actor, "todo", String.valueOf(actor.id()));
        return queries.todo(actor, page, size);
    }

    /** 我已办（当前操作人） */
    public Page<TodoItem> done(int page, int size) { return done(actors.current(), page, size); }

    /** 我处理过的待办，按处理时间从新到旧 */
    public Page<TodoItem> done(U actor, int page, int size) {
        allow(actor, "done", String.valueOf(actor.id()));
        return queries.done(actor, page, size);
    }

    /** 抄送我的（当前操作人） */
    public Page<CcItem> ccList(int page, int size) { return ccList(actors.current(), page, size); }

    /** 抄送我的：未读在前 */
    public Page<CcItem> ccList(U actor, int page, int size) {
        allow(actor, "cc.list", String.valueOf(actor.id()));
        return queries.ccList(actor, page, size);
    }

    /** 我发起的（当前操作人） */
    public Page<ProcessInstance> started(int page, int size) { return started(actors.current(), page, size); }

    /** 我发起的实例，按发起时间从新到旧 */
    public Page<ProcessInstance> started(U actor, int page, int size) {
        allow(actor, "started", String.valueOf(actor.id()));
        return queries.started(actor, page, size);
    }

    /** 实例详情（当前操作人） */
    public InstanceDetail instance(Long instanceId) { return instance(actors.current(), instanceId); }

    /** 实例详情：实例、表单（按流程级的 formView 只读）、全部待办、流转记录、我能不能撤回与催办 */
    public InstanceDetail instance(U actor, Long instanceId) {
        allow(actor, "instance", String.valueOf(instanceId));
        return queries.instance(actor, instanceId);
    }

    private void allow(U actor, String operation, String target) {
        if (!permissions.allows(actor, operation, target)) {
            throw new FlowException(FlowErrorCode.FORBIDDEN, Map.of("operation", operation, "target", String.valueOf(target)));
        }
    }

    /**
     * 组装引擎：没给的扩展点用默认实现（0006 第 6 节）。必须给的只有 {@link #orgDirectory}；不给 {@link #actorProvider} 时，
     * 只能用带 {@code actor} 参数的方法。
     */
    public static final class Builder<U extends Actor> {
        private final ConnectionProvider connections;
        private ActorProvider<U> actors = () -> {
            throw new IllegalStateException("no ActorProvider is configured; pass the actor explicitly or set FlowEngine.Builder.actorProvider");
        };
        private OrgDirectory org;
        private PermissionPolicy<U> permissions = PermissionPolicy.allowAll();
        private Clock clock = Clock.systemUTC();
        private Supplier<Long> tenant = () -> 0L;
        private DefinitionStore definitionStore;
        private FormStore formStore;
        private FormDataStore formDataStore;
        private InstanceStore instanceStore;
        private WorkItemStore workItemStore;
        private EventStore eventStore;
        private JobStore jobStore;
        private CcStore ccStore;
        private WorkCalendar calendar = WorkCalendar.weekdays(ZoneOffset.UTC);
        private final List<JobHandler> jobHandlers = new ArrayList<>();
        private final List<FlowListener> listeners = new ArrayList<>();
        private AfterCommit afterCommit = AfterCommit.immediately();
        private final List<PeopleResolver> resolvers = new ArrayList<>();
        private final List<OutcomeGuard> guards = new ArrayList<>();
        private final List<ServiceAction> actions = new ArrayList<>();

        private Builder(ConnectionProvider connections) { this.connections = Objects.requireNonNull(connections, "connections"); }

        /** 当前操作人从哪来 */
        public Builder<U> actorProvider(ActorProvider<U> actors) { this.actors = actors; return this; }

        /** 接入方的组织架构（必须给：引擎没有默认的组织架构） */
        public Builder<U> orgDirectory(OrgDirectory org) { this.org = org; return this; }

        /** 每个操作前的权限判断；默认全部放行 */
        public Builder<U> permissionPolicy(PermissionPolicy<U> permissions) { this.permissions = permissions; return this; }

        /** 时钟；默认系统时钟（UTC），测试里用固定时钟 */
        public Builder<U> clock(Clock clock) { this.clock = clock; return this; }

        /** 当前租户的编号；默认不用多租户（0） */
        public Builder<U> tenant(Supplier<Long> tenant) { this.tenant = tenant; return this; }

        /** 换掉流程定义的存储 */
        public Builder<U> definitionStore(DefinitionStore store) { this.definitionStore = store; return this; }

        /** 换掉表单的存储 */
        public Builder<U> formStore(FormStore store) { this.formStore = store; return this; }

        /** 换掉表单值的存储 */
        public Builder<U> formDataStore(FormDataStore store) { this.formDataStore = store; return this; }

        /** 换掉实例的存储 */
        public Builder<U> instanceStore(InstanceStore store) { this.instanceStore = store; return this; }

        /** 换掉待办的存储 */
        public Builder<U> workItemStore(WorkItemStore store) { this.workItemStore = store; return this; }

        /** 换掉流转记录的存储 */
        public Builder<U> eventStore(EventStore store) { this.eventStore = store; return this; }

        /** 换掉抄送的存储 */
        public Builder<U> ccStore(CcStore store) { this.ccStore = store; return this; }

        /** 换掉作业的存储 */
        public Builder<U> jobStore(JobStore store) { this.jobStore = store; return this; }

        /** 工作日历：时限写成工作日时按它算；默认周一到周五、UTC 的日期（{@link WorkCalendar#weekdays}） */
        public Builder<U> workCalendar(WorkCalendar calendar) { this.calendar = calendar; return this; }

        /** 注册一个处理人解析器（定义里的 {@code { "resolver": 名字 }}）；同名的后注册的换掉先注册的 */
        public Builder<U> resolver(PeopleResolver resolver) { this.resolvers.add(resolver); return this; }

        /** 注册一个提交前的守卫（处理结果的 {@code guards}） */
        public Builder<U> guard(OutcomeGuard guard) { this.guards.add(guard); return this; }

        /** 注册一个自动节点的动作（{@code service} 节点的 {@code action}） */
        public Builder<U> action(ServiceAction action) { this.actions.add(action); return this; }

        /** 注册一个监听：发通知、同步业务表、审计（{@link FlowListener}） */
        public Builder<U> listener(FlowListener listener) { this.listeners.add(listener); return this; }

        /** 提交后的监听怎么排：默认引擎的事务做完马上调；命令会加入外面的事务时换成那个事务的提交后回调 */
        public Builder<U> afterCommit(AfterCommit afterCommit) { this.afterCommit = afterCommit; return this; }

        /** 注册一种作业的处理（新的种类，或换掉内置的 timer、overdue、remind、unclaimed） */
        public Builder<U> jobHandler(JobHandler handler) { this.jobHandlers.add(handler); return this; }

        /** 组装 */
        public FlowEngine<U> build() {
            if (org == null) throw new IllegalStateException("an OrgDirectory is required: the engine has no default organisation");
            // 工作进程替所有租户做作业：做每个作业时切到它的租户（TenantScope），其余时候是接入方给的当前租户
            Database db = FlowDatabase.create(connections, TenantScope.over(tenant), clock);
            Stores defaults = QueryStores.create(db);
            Stores stores = new Stores(
                definitionStore != null ? definitionStore : defaults.definitions(),
                formStore != null ? formStore : defaults.forms(),
                formDataStore != null ? formDataStore : defaults.formData(),
                instanceStore != null ? instanceStore : defaults.instances(),
                workItemStore != null ? workItemStore : defaults.workItems(),
                eventStore != null ? eventStore : defaults.events(),
                jobStore != null ? jobStore : defaults.jobs(),
                ccStore != null ? ccStore : defaults.ccs());
            return new FlowEngine<>(new EngineContext(db, stores, clock, org, calendar, new EventDispatcher(List.copyOf(listeners), afterCommit),
                new Extensions(resolvers, guards, actions)), actors, permissions, List.copyOf(jobHandlers));
        }
    }
}
