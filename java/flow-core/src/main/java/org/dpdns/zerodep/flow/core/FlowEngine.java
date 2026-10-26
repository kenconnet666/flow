package org.dpdns.zerodep.flow.core;

import org.dpdns.zerodep.flow.core.command.ClaimCommand;
import org.dpdns.zerodep.flow.core.command.CommandResult;
import org.dpdns.zerodep.flow.core.command.CompleteCommand;
import org.dpdns.zerodep.flow.core.command.FormPublication;
import org.dpdns.zerodep.flow.core.command.InstanceDetail;
import org.dpdns.zerodep.flow.core.command.StartCommand;
import org.dpdns.zerodep.flow.core.command.WithdrawCommand;
import org.dpdns.zerodep.flow.core.engine.ClaimHandler;
import org.dpdns.zerodep.flow.core.engine.CompleteHandler;
import org.dpdns.zerodep.flow.core.engine.DefinitionCache;
import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.engine.Publishing;
import org.dpdns.zerodep.flow.core.engine.Queries;
import org.dpdns.zerodep.flow.core.engine.StartHandler;
import org.dpdns.zerodep.flow.core.engine.WithdrawHandler;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.identity.ActorProvider;
import org.dpdns.zerodep.flow.core.identity.OrgDirectory;
import org.dpdns.zerodep.flow.core.identity.PermissionPolicy;
import org.dpdns.zerodep.flow.core.store.DefinitionStore;
import org.dpdns.zerodep.flow.core.store.DefinitionStore.StoredDefinition;
import org.dpdns.zerodep.flow.core.store.EventStore;
import org.dpdns.zerodep.flow.core.store.FormDataStore;
import org.dpdns.zerodep.flow.core.store.FormStore;
import org.dpdns.zerodep.flow.core.store.FormStore.StoredForm;
import org.dpdns.zerodep.flow.core.store.InstanceStore;
import org.dpdns.zerodep.flow.core.store.Stores;
import org.dpdns.zerodep.flow.core.store.WorkItemStore;
import org.dpdns.zerodep.flow.core.store.query.FlowDatabase;
import org.dpdns.zerodep.flow.core.store.query.QueryStores;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;
import org.dpdns.zerodep.flow.model.runtime.TodoItem;
import org.dpdns.zerodep.query.exec.ConnectionProvider;
import org.dpdns.zerodep.query.exec.Database;
import org.dpdns.zerodep.query.exec.Page;

import javax.sql.DataSource;
import java.time.Clock;
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
    private final Publishing publishing;
    private final Queries queries;

    private FlowEngine(EngineContext context, ActorProvider<U> actors, PermissionPolicy<U> permissions) {
        this.actors = actors;
        this.permissions = permissions;
        DefinitionCache definitions = new DefinitionCache(context);
        this.starts = new StartHandler(context, definitions);
        this.claims = new ClaimHandler(context, definitions);
        this.completions = new CompleteHandler(context, definitions, claims);
        this.withdrawals = new WithdrawHandler(context, definitions);
        this.publishing = new Publishing(context);
        this.queries = new Queries(context);
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

    // ───────── 查询 ─────────

    /** 我的待办（当前操作人） */
    public Page<TodoItem> todo(int page, int size) { return todo(actors.current(), page, size); }

    /** 我的待办：落到我的，加上我能认领的；按到达时间从新到旧，带实例的标题与摘要列 */
    public Page<TodoItem> todo(U actor, int page, int size) {
        allow(actor, "todo", String.valueOf(actor.id()));
        return queries.todo(actor, page, size);
    }

    /** 实例详情（当前操作人） */
    public InstanceDetail instance(Long instanceId) { return instance(actors.current(), instanceId); }

    /** 实例详情：实例、表单的值、全部待办、流转记录 */
    public InstanceDetail instance(U actor, Long instanceId) {
        allow(actor, "instance", String.valueOf(instanceId));
        return queries.instance(instanceId);
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

        /** 组装 */
        public FlowEngine<U> build() {
            if (org == null) throw new IllegalStateException("an OrgDirectory is required: the engine has no default organisation");
            Database db = FlowDatabase.create(connections, tenant, clock);
            Stores defaults = QueryStores.create(db);
            Stores stores = new Stores(
                definitionStore != null ? definitionStore : defaults.definitions(),
                formStore != null ? formStore : defaults.forms(),
                formDataStore != null ? formDataStore : defaults.formData(),
                instanceStore != null ? instanceStore : defaults.instances(),
                workItemStore != null ? workItemStore : defaults.workItems(),
                eventStore != null ? eventStore : defaults.events());
            return new FlowEngine<>(new EngineContext(db, stores, clock, org), actors, permissions);
        }
    }
}
