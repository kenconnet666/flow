package org.dpdns.zerodep.flow.core;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import org.dpdns.zerodep.flow.core.calendar.WorkCalendar;
import org.dpdns.zerodep.flow.core.command.AddSignersCommand;
import org.dpdns.zerodep.flow.core.command.AssignCommand;
import org.dpdns.zerodep.flow.core.command.CcCommand;
import org.dpdns.zerodep.flow.core.command.ClaimCommand;
import org.dpdns.zerodep.flow.core.command.CommandInterceptor;
import org.dpdns.zerodep.flow.core.command.CommandResult;
import org.dpdns.zerodep.flow.core.command.CommentCommand;
import org.dpdns.zerodep.flow.core.command.CompleteCommand;
import org.dpdns.zerodep.flow.core.command.DelegateCommand;
import org.dpdns.zerodep.flow.core.command.FlowCommand;
import org.dpdns.zerodep.flow.core.command.FormPublication;
import org.dpdns.zerodep.flow.core.command.JumpCommand;
import org.dpdns.zerodep.flow.core.command.ReadCcCommand;
import org.dpdns.zerodep.flow.core.command.ReassignCommand;
import org.dpdns.zerodep.flow.core.command.ReleaseCommand;
import org.dpdns.zerodep.flow.core.command.RemindCommand;
import org.dpdns.zerodep.flow.core.command.RemoveSignersCommand;
import org.dpdns.zerodep.flow.core.command.ResumeCommand;
import org.dpdns.zerodep.flow.core.command.RetryJobCommand;
import org.dpdns.zerodep.flow.core.command.SaveDraftCommand;
import org.dpdns.zerodep.flow.core.command.SignalCommand;
import org.dpdns.zerodep.flow.core.command.SkipJobCommand;
import org.dpdns.zerodep.flow.core.command.StartCommand;
import org.dpdns.zerodep.flow.core.command.SuspendCommand;
import org.dpdns.zerodep.flow.core.command.TerminateCommand;
import org.dpdns.zerodep.flow.core.command.TransferCommand;
import org.dpdns.zerodep.flow.core.command.WithdrawCommand;
import org.dpdns.zerodep.flow.core.command.WithdrawStepCommand;
import org.dpdns.zerodep.flow.core.db.FlowEasyQuery;
import org.dpdns.zerodep.flow.core.db.FlowInterceptors;
import org.dpdns.zerodep.flow.core.engine.DefinitionCache;
import org.dpdns.zerodep.flow.core.engine.EngineContext;
import org.dpdns.zerodep.flow.core.engine.EventDispatcher;
import org.dpdns.zerodep.flow.core.engine.TenantScope;
import org.dpdns.zerodep.flow.core.engine.handler.AdminHandler;
import org.dpdns.zerodep.flow.core.engine.handler.ClaimHandler;
import org.dpdns.zerodep.flow.core.engine.handler.CollaborationHandler;
import org.dpdns.zerodep.flow.core.engine.handler.CompleteHandler;
import org.dpdns.zerodep.flow.core.engine.handler.Publishing;
import org.dpdns.zerodep.flow.core.engine.handler.SignalHandler;
import org.dpdns.zerodep.flow.core.engine.handler.StartHandler;
import org.dpdns.zerodep.flow.core.engine.handler.StepWithdrawal;
import org.dpdns.zerodep.flow.core.engine.handler.WithdrawHandler;
import org.dpdns.zerodep.flow.core.engine.job.BuiltinJobs;
import org.dpdns.zerodep.flow.core.engine.job.JobRunner;
import org.dpdns.zerodep.flow.core.engine.query.Queries.InstanceSearch;
import org.dpdns.zerodep.flow.core.engine.query.Queries;
import org.dpdns.zerodep.flow.core.entity.FormVersionEntity;
import org.dpdns.zerodep.flow.core.entity.ProcessVersionEntity;
import org.dpdns.zerodep.flow.core.entity.RequestEntity;
import org.dpdns.zerodep.flow.core.extension.Extensions;
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
import org.dpdns.zerodep.flow.core.view.Page;
import org.dpdns.zerodep.flow.core.view.definition.DefinitionProblem;
import org.dpdns.zerodep.flow.core.view.definition.ProcessSummary;
import org.dpdns.zerodep.flow.core.view.definition.ProcessVersionVO;
import org.dpdns.zerodep.flow.core.view.definition.StartView;
import org.dpdns.zerodep.flow.core.view.definition.StartableProcess;
import org.dpdns.zerodep.flow.core.view.form.FormDocument;
import org.dpdns.zerodep.flow.core.view.form.FormSummary;
import org.dpdns.zerodep.flow.core.view.form.FormVersionVO;
import org.dpdns.zerodep.flow.core.view.instance.CcItem;
import org.dpdns.zerodep.flow.core.view.instance.IncidentView;
import org.dpdns.zerodep.flow.core.view.instance.InstanceDetail;
import org.dpdns.zerodep.flow.core.view.instance.InstanceVO;
import org.dpdns.zerodep.flow.core.view.workitem.TodoItem;
import org.dpdns.zerodep.flow.core.view.workitem.WorkItemView;
import org.dpdns.zerodep.flow.model.error.ErrorDetail;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * 流程引擎的门面（docs/decisions/0006）：接入方只和它打交道。命令改状态，查询读，扩展点由 {@link Builder} 给出。不依赖 Spring。
 * <pre>{@code
 * FlowEngine<MyUser> engine = FlowEngine.<MyUser>builder(easyEntityQuery).actorProvider(security::currentUser).orgDirectory(org).build();
 * engine.publishForm(new FormPublication("purchase", "采购申请", null, manifestJson, null, null, null, null, null, null));
 * engine.publishDefinition(definitionJson, "第一版");
 * CommandResult started = engine.start(StartCommand.of("purchase", Map.of("amount", "1200.00")));
 * engine.complete(CompleteCommand.of(workItemId, "approve"));
 * Page<TodoItem> todo = engine.todo(1, 20);
 * }</pre>
 * <ul>
 *   <li>表由接入方建：执行 flow-core 里的 {@code db/flow/mysql/*.sql}，推荐交给 Flyway（docs/decisions/0007 第 9 节）；</li>
 *   <li>读写用接入方的 easy-query（{@code EasyEntityQuery}）：表是 {@code entity} 包里的实体，接入方也能直接查它们、和自己的表一起查；</li>
 *   <li>每个命令在一个事务里执行（外面已有事务时加入），处理的步骤见各处理类（{@code engine} 包）；</li>
 *   <li>出错时抛带业务错误码的 {@link FlowException}（0008），HTTP 接口写成响应体；</li>
 *   <li>不带操作人的方法取 {@link ActorProvider} 的当前操作人，带 {@code actor} 的给后台任务、系统操作用；每个操作先问 {@link PermissionPolicy}；</li>
 *   <li>命令过了权限再过 {@link CommandInterceptor}（审计、业务校验、改参数），和标准处理在同一个事务里。</li>
 * </ul>
 */
public class FlowEngine<U extends Actor> {
    private final ActorProvider<U> actors;
    private final PermissionPolicy<U> permissions;
    private final StartHandler starts;
    private final ClaimHandler claims;
    private final CompleteHandler completions;
    private final WithdrawHandler withdrawals;
    private final CollaborationHandler collaboration;
    private final AdminHandler admin;
    private final SignalHandler signals;
    private final StepWithdrawal stepWithdrawals;
    private final Publishing publishing;
    private final Queries queries;
    private final JobRunner jobs;
    private final EngineContext context;
    private final List<CommandInterceptor<U>> interceptors;

    private FlowEngine(EngineContext context, ActorProvider<U> actors, PermissionPolicy<U> permissions, List<CommandInterceptor<U>> interceptors,
                       List<JobHandler> jobHandlers) {
        this.context = context;
        this.actors = actors;
        this.permissions = permissions;
        this.interceptors = interceptors;
        this.starts = new StartHandler(context);
        this.claims = new ClaimHandler(context);
        this.completions = new CompleteHandler(context, claims);
        this.withdrawals = new WithdrawHandler(context);
        this.collaboration = new CollaborationHandler(context, claims);
        this.admin = new AdminHandler(context);
        this.signals = new SignalHandler(context);
        this.stepWithdrawals = new StepWithdrawal(context);
        this.publishing = new Publishing(context);
        this.queries = new Queries(context);
        // 内置的作业在前，接入方注册的在后：同一种时接入方的换掉内置的
        List<JobHandler> handlers = new ArrayList<>(new BuiltinJobs(context, completions).handlers());
        handlers.addAll(jobHandlers);
        this.jobs = new JobRunner(context, handlers);
    }

    /**
     * 开始组装：用接入方建好的 easy-query（方言、数据源、事务由它定；Spring 里是 easy-query starter 的那个）。
     * {@link Builder#build} 时把引擎的表要的转换器与拦截器装上去（{@link FlowEasyQuery#install}）。
     */
    public static <U extends Actor> Builder<U> builder(EasyEntityQuery db) { return new Builder<>(db); }

    // ───────── 发布 ─────────

    /** 发布流程定义（当前操作人），见 {@link #publishDefinition(Actor, String, String)} */
    public ProcessVersionEntity publishDefinition(String json, String note) { return publishDefinition(actors.current(), json, note); }

    /** 发布流程定义：校验不通过时报 {@code flow.definition-invalid}，错误明细里是问题列表；版本号由后端给 */
    public ProcessVersionEntity publishDefinition(U actor, String json, String note) {
        allow(actor, "definitions.publish", null);
        return publishing.publishDefinition(actor, json, note);
    }

    /** 校验定义、不发布（当前操作人） */
    public List<DefinitionProblem> validateDefinition(String json) { return validateDefinition(actors.current(), json); }

    /** 校验一份定义、不发布：错误与警告都列出来（设计、编辑定义时用，{@code definitions.validate}） */
    public List<DefinitionProblem> validateDefinition(U actor, String json) {
        allow(actor, "definitions.validate", null);
        return publishing.validateDefinition(json);
    }

    /** 停用或启用流程（当前操作人） */
    public ProcessSummary setProcessActive(String processKey, boolean active) { return setProcessActive(actors.current(), processKey, active); }

    /** 停用或启用流程：停用后不能再发起，进行中的照常走完（{@code definitions.status}） */
    public ProcessSummary setProcessActive(U actor, String processKey, boolean active) {
        allow(actor, "definitions.status", processKey);
        return publishing.setStatus(processKey, active);
    }

    /** 流程的全部版本（当前操作人） */
    public List<ProcessVersionVO> versions(String processKey) { return versions(actors.current(), processKey); }

    /** 流程的全部版本，新的在前（{@code definitions.versions}） */
    public List<ProcessVersionVO> versions(U actor, String processKey) {
        allow(actor, "definitions.versions", processKey);
        return queries.versions(processKey);
    }

    /** 某个版本的定义 JSON（当前操作人） */
    public String definitionJson(String processKey, Integer version) { return definitionJson(actors.current(), processKey, version); }

    /** 某个版本的定义 JSON（发布时的原样）；version 为 null 时取最新的（{@code definitions.read}） */
    public String definitionJson(U actor, String processKey, Integer version) {
        allow(actor, "definitions.read", processKey);
        return queries.definitionJson(processKey, version);
    }

    /** 表单列表（当前操作人） */
    public List<FormSummary> forms() { return forms(actors.current()); }

    /** 管理端的表单列表：停用的也在，按分类、名称排（{@code forms.list}） */
    public List<FormSummary> forms(U actor) {
        allow(actor, "forms.list", null);
        return queries.forms();
    }

    /** 表单的全部版本（当前操作人） */
    public List<FormVersionVO> formVersions(String formKey) { return formVersions(actors.current(), formKey); }

    /** 表单的全部版本，新的在前（{@code forms.versions}） */
    public List<FormVersionVO> formVersions(U actor, String formKey) {
        allow(actor, "forms.versions", formKey);
        return queries.formVersions(formKey);
    }

    /** 表单某个版本的全部内容（当前操作人） */
    public FormDocument formDocument(String formKey, Integer version) { return formDocument(actors.current(), formKey, version); }

    /** 表单某个版本的源码与字段清单（编辑时载入）；version 为 null 时取最新的（{@code forms.read}） */
    public FormDocument formDocument(U actor, String formKey, Integer version) {
        allow(actor, "forms.read", formKey);
        return queries.formDocument(formKey, version);
    }

    /** 停用或启用表单（当前操作人） */
    public FormSummary setFormActive(String formKey, boolean active) { return setFormActive(actors.current(), formKey, active); }

    /** 停用或启用表单：停用后新发布的流程不能再选它，已经引用它的照常用（{@code forms.status}） */
    public FormSummary setFormActive(U actor, String formKey, boolean active) {
        allow(actor, "forms.status", formKey);
        return publishing.setFormStatus(formKey, active);
    }

    /** 发布表单（当前操作人） */
    public FormVersionEntity publishForm(FormPublication form) { return publishForm(actors.current(), form); }

    /** 发布表单：字段清单校验不通过时报 {@code flow.validation-failed} */
    public FormVersionEntity publishForm(U actor, FormPublication form) {
        allow(actor, "forms.publish", form.getKey());
        return publishing.publishForm(actor, form);
    }

    // ───────── 命令 ─────────

    /** 发起（当前操作人） */
    public CommandResult start(StartCommand command) { return start(actors.current(), command); }

    /** 发起：按最新发布的版本走（步骤见 {@link StartHandler}） */
    public CommandResult start(U actor, StartCommand command) {
        return execute(actor, "start", command.getKey(), command, starts::handle);
    }

    /** 认领（当前操作人） */
    public CommandResult claim(ClaimCommand command) { return claim(actors.current(), command); }

    /** 认领收件箱里的待办：先到先得（见 {@link ClaimHandler}） */
    public CommandResult claim(U actor, ClaimCommand command) {
        return execute(actor, "claim", String.valueOf(command.getWorkItemId()), command, claims::handle);
    }

    /** 处理（当前操作人） */
    public CommandResult complete(CompleteCommand command) { return complete(actors.current(), command); }

    /** 处理一个待办（步骤见 {@link CompleteHandler}） */
    public CommandResult complete(U actor, CompleteCommand command) {
        return execute(actor, "complete", String.valueOf(command.getWorkItemId()), command, completions::handle);
    }

    /** 暂存（当前操作人） */
    public CommandResult saveDraft(SaveDraftCommand command) { return saveDraft(actors.current(), command); }

    /** 暂存：填了一半先存在待办上，不提交（见 {@link CompleteHandler#saveDraft}） */
    public CommandResult saveDraft(U actor, SaveDraftCommand command) {
        return execute(actor, "saveDraft", String.valueOf(command.getWorkItemId()), command, completions::saveDraft);
    }

    /** 撤回（当前操作人） */
    public CommandResult withdraw(WithdrawCommand command) { return withdraw(actors.current(), command); }

    /** 发起人撤回整个实例（规则见 {@link WithdrawHandler}） */
    public CommandResult withdraw(U actor, WithdrawCommand command) {
        return execute(actor, "withdraw", String.valueOf(command.getInstanceId()), command, withdrawals::handle);
    }

    // ───────── 协作与管理员命令（0006 的 3.1–3.4） ─────────

    /** 转办（当前操作人） */
    public CommandResult transfer(TransferCommand command) { return transfer(actors.current(), command); }

    /** 转办：交给别人，自己不再参与 */
    public CommandResult transfer(U actor, TransferCommand command) {
        return execute(actor, "transfer", String.valueOf(command.getWorkItemId()), command, collaboration::transfer);
    }

    /** 委派（当前操作人） */
    public CommandResult delegate(DelegateCommand command) { return delegate(actors.current(), command); }

    /** 委派：请别人先办，办完回到自己手里 */
    public CommandResult delegate(U actor, DelegateCommand command) {
        return execute(actor, "delegate", String.valueOf(command.getWorkItemId()), command, collaboration::delegate);
    }

    /** 加签（当前操作人） */
    public CommandResult addSigners(AddSignersCommand command) { return addSigners(actors.current(), command); }

    /** 加签：前加签、后加签、并加 */
    public CommandResult addSigners(U actor, AddSignersCommand command) {
        return execute(actor, "addSigners", String.valueOf(command.getWorkItemId()), command, collaboration::addSigners);
    }

    /** 减签（当前操作人） */
    public CommandResult removeSigners(RemoveSignersCommand command) { return removeSigners(actors.current(), command); }

    /** 减签：取消自己加的、还没办的人 */
    public CommandResult removeSigners(U actor, RemoveSignersCommand command) {
        return execute(actor, "removeSigners", String.valueOf(command.getWorkItemId()), command, collaboration::removeSigners);
    }

    /** 退回收件箱（当前操作人） */
    public CommandResult release(ReleaseCommand command) { return release(actors.current(), command); }

    /** 退回收件箱：自己以后不能再认领这一条 */
    public CommandResult release(U actor, ReleaseCommand command) {
        return execute(actor, "release", String.valueOf(command.getWorkItemId()), command, collaboration::release);
    }

    /** 催办（当前操作人） */
    public CommandResult remind(RemindCommand command) { return remind(actors.current(), command); }

    /** 催办：同一个待办一小时内只记一次 */
    public CommandResult remind(U actor, RemindCommand command) {
        return execute(actor, "remind", String.valueOf(command.getInstanceId()), command, collaboration::remind);
    }

    /** 留言（当前操作人） */
    public CommandResult comment(CommentCommand command) { return comment(actors.current(), command); }

    /** 留言：记进流转记录 */
    public CommandResult comment(U actor, CommentCommand command) {
        return execute(actor, "comment", String.valueOf(command.getInstanceId()), command, collaboration::comment);
    }

    /** 撤回刚处理的一步（当前操作人） */
    public CommandResult withdrawStep(WithdrawStepCommand command) { return withdrawStep(actors.current(), command); }

    /** 处理人撤回刚处理的一步：下一步还没人动时，待办回到自己手里（条件见 {@link StepWithdrawal}） */
    public CommandResult withdrawStep(U actor, WithdrawStepCommand command) {
        return execute(actor, "withdrawStep", String.valueOf(command.getWorkItemId()), command, stepWithdrawals::withdraw);
    }

    /** 外部事件（当前操作人） */
    public List<CommandResult> signal(SignalCommand command) { return signal(actors.current(), command); }

    /** 外部事件：在等它的实例往下走，返回每个实例这一步的结果；实例不在等时只记一笔（见 {@link SignalHandler}） */
    public List<CommandResult> signal(U actor, SignalCommand command) {
        return execute(actor, "signal", command.getInstanceId() != null ? String.valueOf(command.getInstanceId()) : command.getBusinessKey(), command, signals::signal);
    }

    /** 收件箱负责人指定承办人（当前操作人） */
    public CommandResult assign(AssignCommand command) { return assign(actors.current(), command); }

    /** 收件箱负责人指定承办人：只有等我指定的待办能指定 */
    public CommandResult assign(U actor, AssignCommand command) {
        return execute(actor, "assign", String.valueOf(command.getWorkItemId()), command, collaboration::assign);
    }

    /** 抄送（当前操作人） */
    public CommandResult cc(CcCommand command) { return cc(actors.current(), command); }

    /** 抄送（知会）：每人一条抄送，记进流转记录 */
    public CommandResult cc(U actor, CcCommand command) {
        return execute(actor, "cc", String.valueOf(command.getInstanceId()), command, collaboration::cc);
    }

    /** 读了抄送（当前操作人） */
    public int readCc(ReadCcCommand command) { return readCc(actors.current(), command); }

    /** 读了抄送：一个实例抄送给我的都标成已读，返回标了几条 */
    public int readCc(U actor, ReadCcCommand command) {
        return execute(actor, "readCc", String.valueOf(command.getInstanceId()), command, collaboration::readCc);
    }

    /** 终止（管理员）（当前操作人） */
    public CommandResult terminate(TerminateCommand command) { return terminate(actors.current(), command); }

    /** 终止（管理员）：打开着的待办都取消，实例结束 */
    public CommandResult terminate(U actor, TerminateCommand command) {
        return execute(actor, "terminate", String.valueOf(command.getInstanceId()), command, admin::terminate);
    }

    /** 暂停（管理员）（当前操作人） */
    public CommandResult suspend(SuspendCommand command) { return suspend(actors.current(), command); }

    /** 暂停（管理员）：待办都不能处理，直到恢复 */
    public CommandResult suspend(U actor, SuspendCommand command) {
        return execute(actor, "suspend", String.valueOf(command.getInstanceId()), command, admin::suspend);
    }

    /** 恢复（管理员）（当前操作人） */
    public CommandResult resume(ResumeCommand command) { return resume(actors.current(), command); }

    /** 恢复（管理员） */
    public CommandResult resume(U actor, ResumeCommand command) {
        return execute(actor, "resume", String.valueOf(command.getInstanceId()), command, admin::resume);
    }

    /** 跳转（管理员）（当前操作人） */
    public CommandResult jump(JumpCommand command) { return jump(actors.current(), command); }

    /** 跳转（管理员）：原来的待办取消，从指定的节点重新走 */
    public CommandResult jump(U actor, JumpCommand command) {
        return execute(actor, "jump", String.valueOf(command.getInstanceId()), command, admin::jump);
    }

    /** 改派（管理员、收件箱负责人）（当前操作人） */
    public CommandResult reassign(ReassignCommand command) { return reassign(actors.current(), command); }

    /** 改派（管理员、收件箱负责人）：待办直接落到这个人 */
    public CommandResult reassign(U actor, ReassignCommand command) {
        return execute(actor, "reassign", String.valueOf(command.getWorkItemId()), command, admin::reassign);
    }

    /** 失败的作业重新来过（管理员）（当前操作人） */
    public boolean retryJob(RetryJobCommand command) { return retryJob(actors.current(), command); }

    /** 失败的作业重新来过（管理员）：修好外部系统后让异步的自动节点再做一次 */
    public boolean retryJob(U actor, RetryJobCommand command) {
        return execute(actor, "retryJob", String.valueOf(command.getJobId()), command, admin::retryJob);
    }

    /** 跳过失败的作业（管理员）（当前操作人） */
    public CommandResult skipJob(SkipJobCommand command) { return skipJob(actors.current(), command); }

    /** 跳过失败的作业（管理员）：异步的自动节点不再做动作，按指定的结果往下走 */
    public CommandResult skipJob(U actor, SkipJobCommand command) {
        return execute(actor, "skipJob", String.valueOf(command.getJobId()), command, admin::skipJob);
    }

    // ───────── 幂等 ─────────

    /** 幂等地做一个命令（当前操作人），见 {@link #idempotent(Actor, String, String, Supplier)} */
    public String idempotent(String requestId, String command, Supplier<String> work) {
        return idempotent(actors.current(), requestId, command, work);
    }

    /**
     * 幂等地做一个命令（0006 第 1 节 {@code requestId}，HTTP 的 {@code Idempotency-Key}）：这个请求编号没做过时做 work，
     * 把它给出的响应与命令在同一个事务里记下；做过时直接返回第一次的响应。同一个编号用在别的命令上、换了人时报
     * {@code flow.validation-failed}（{@code requestId: reused}）。work 失败时什么也不记，再来一次照常做。
     *
     * @param command 命令的名字（{@code complete}……）
     * @param work    做命令，返回要记下与返回的响应（HTTP 接口里是整个响应体）
     */
    public String idempotent(U actor, String requestId, String command, Supplier<String> work) {
        if (requestId == null || requestId.isBlank()) return work.get();
        return context.transaction(() -> {
            RequestEntity done = context.getDb().queryable(RequestEntity.class).where(r -> r.requestId().eq(requestId)).singleOrNull();
            if (done != null) {
                if (!done.getCommand().equals(command) || !Objects.equals(done.getActorId(), actor.getId())) {
                    throw new FlowException(FlowErrorCode.VALIDATION_FAILED, "request id " + requestId + " was used for another command",
                        List.of(new ErrorDetail("reused", "requestId", null, null, null, Map.of())));
                }
                return done.getResponse();
            }
            String response = work.get();
            // 同一个编号并发写第二次时违反唯一约束，那个命令整个回滚
            RequestEntity request = new RequestEntity();
            request.setRequestId(requestId);
            request.setCommand(command);
            request.setActorId(actor.getId());
            request.setResponse(response);
            request.setCreatedAt(context.getClock().instant());
            context.getDb().insertable(request).executeRows();
            return response;
        });
    }

    // ───────── 作业 ─────────

    /**
     * 做一批到期的作业（定时节点到点、待办超时、提醒、无人认领），返回做了几个。Spring Boot 里默认定时调它（{@code flow.jobs.*}）；
     * 交给已有的调度平台时关掉默认的、由平台调它。几个进程同时调不会重复做（{@link JobRunner}）。
     */
    public int runDueJobs() { return jobs.runDue(); }

    /**
     * 删掉过期的幂等记录（{@code flow_request}，全部租户）：早于 olderThan 之前的请求编号不再认，同一个编号再来会当新请求做。
     * 调用方的重试都在这个窗口里才安全，默认留 24 小时。Spring Boot 里由 {@code FlowJobPoller} 每小时调一次（{@code flow.idempotency.retention}）；
     * 关掉默认的作业轮询时由调度平台调它。返回删了几条。
     */
    public long purgeRequests(Duration olderThan) {
        Instant cutoff = context.getClock().instant().minus(olderThan);
        return context.transaction(() -> context.getDb().deletable(RequestEntity.class).allowDeleteStatement(true)
            .noInterceptor(FlowInterceptors.TENANT)
            .where(r -> r.createdAt().lt(cutoff))
            .executeRows());
    }

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
        allow(actor, "todo", String.valueOf(actor.getId()));
        return queries.todo(actor, page, size);
    }

    /** 等我指定承办人的待办（当前操作人） */
    public Page<TodoItem> designations(int page, int size) { return designations(actors.current(), page, size); }

    /** 等我指定承办人的待办：无人认领超时、我是收件箱负责人（{@code designations}） */
    public Page<TodoItem> designations(U actor, int page, int size) {
        allow(actor, "designations", String.valueOf(actor.getId()));
        return queries.designations(actor, page, size);
    }

    /** 我已办（当前操作人） */
    public Page<TodoItem> done(int page, int size) { return done(actors.current(), page, size); }

    /** 我处理过的待办，按处理时间从新到旧 */
    public Page<TodoItem> done(U actor, int page, int size) {
        allow(actor, "done", String.valueOf(actor.getId()));
        return queries.done(actor, page, size);
    }

    /** 抄送我的（当前操作人） */
    public Page<CcItem> ccList(int page, int size) { return ccList(actors.current(), page, size); }

    /** 抄送我的：未读在前 */
    public Page<CcItem> ccList(U actor, int page, int size) {
        allow(actor, "cc.list", String.valueOf(actor.getId()));
        return queries.ccList(actor, page, size);
    }

    /** 管理端搜实例（当前操作人） */
    public Page<InstanceVO> instances(InstanceSearch search, int page, int size) { return instances(actors.current(), search, page, size); }

    /** 管理端搜实例：按状态、流程、标题、发起人；谁能搜由 PermissionPolicy 定（{@code instances.search}） */
    public Page<InstanceVO> instances(U actor, InstanceSearch search, int page, int size) {
        allow(actor, "instances.search", null);
        return queries.instances(search, page, size);
    }

    /** 管理端的流程列表（当前操作人） */
    public List<ProcessSummary> processes() { return processes(actors.current()); }

    /** 管理端的流程列表：全部流程，停用的也在（{@code processes.list}） */
    public List<ProcessSummary> processes(U actor) {
        allow(actor, "processes.list", null);
        return queries.processes();
    }

    /** 异常（当前操作人） */
    public Page<IncidentView> incidents(Long instanceId, int page, int size) { return incidents(actors.current(), instanceId, page, size); }

    /**
     * 异常：重试用完的作业，新的在前（{@code incidents}）；instanceId 不为 null 时只看这个实例的。
     * 管理员修好外部系统后 retryJob，或 skipJob 按结果往下走，或终止实例
     */
    public Page<IncidentView> incidents(U actor, Long instanceId, int page, int size) {
        allow(actor, "incidents", instanceId == null ? null : String.valueOf(instanceId));
        return queries.incidents(instanceId, page, size);
    }

    /** 我发起的（当前操作人） */
    public Page<InstanceVO> started(int page, int size) { return started(actors.current(), page, size); }

    /** 我发起的实例，按发起时间从新到旧 */
    public Page<InstanceVO> started(U actor, int page, int size) {
        allow(actor, "started", String.valueOf(actor.getId()));
        return queries.started(actor, page, size);
    }

    /** 实例详情（当前操作人） */
    public InstanceDetail instance(Long instanceId) { return instance(actors.current(), instanceId); }

    /** 实例详情：实例、表单（按流程级的 formView 只读）、全部待办、流转记录、我能不能撤回与催办 */
    public InstanceDetail instance(U actor, Long instanceId) {
        allow(actor, "instance", String.valueOf(instanceId));
        return queries.instance(actor, instanceId);
    }

    /**
     * 命令的管道（0006 第 1 节）：权限 → 在一个事务里依次过拦截器（先注册的在外层）、做标准处理 → 提交 → 提交后的通知。
     * 拦截器换过的命令要和原来的同类型。
     */
    private <C extends FlowCommand, R> R execute(U actor, String operation, String target, C command, BiFunction<U, C, R> handler) {
        allow(actor, operation, target);
        if (interceptors.isEmpty()) return handler.apply(actor, command);
        @SuppressWarnings("unchecked")
        Class<C> type = (Class<C>) command.getClass();   // 命令都是记录（final），类型就是 C
        return context.transaction(() -> chain(0, actor, operation, type, handler).proceed(command));
    }

    /** 从第 index 个拦截器往下的那一段；拦截器都过完是标准处理 */
    private <C extends FlowCommand, R> CommandInterceptor.Chain<R> chain(int index, U actor, String operation, Class<C> type,
                                                                     BiFunction<U, C, R> handler) {
        if (index == interceptors.size()) {
            return command -> {
                if (!type.isInstance(command)) {
                    throw new IllegalArgumentException("a CommandInterceptor must proceed with a " + type.getSimpleName() + " for " + operation);
                }
                return handler.apply(actor, type.cast(command));
            };
        }
        CommandInterceptor<U> interceptor = interceptors.get(index);
        CommandInterceptor.Chain<R> rest = chain(index + 1, actor, operation, type, handler);
        return command -> interceptor.intercept(actor, operation, command, rest);
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
    public static class Builder<U extends Actor> {
        private final EasyEntityQuery db;
        private ActorProvider<U> actors = () -> {
            throw new IllegalStateException("no ActorProvider is configured; pass the actor explicitly or set FlowEngine.Builder.actorProvider");
        };
        private OrgDirectory org;
        private PermissionPolicy<U> permissions = PermissionPolicy.allowAll();
        private Clock clock = Clock.systemUTC();
        private Supplier<Long> tenant = () -> 0L;
        private WorkCalendar calendar = WorkCalendar.weekdays(ZoneOffset.UTC);
        private final List<JobHandler> jobHandlers = new ArrayList<>();
        private final List<FlowListener> listeners = new ArrayList<>();
        private final List<NotificationChannel> channels = new ArrayList<>();
        private final List<CommandInterceptor<U>> interceptors = new ArrayList<>();
        private AfterCommit afterCommit = AfterCommit.immediately();
        private final List<PeopleResolver> resolvers = new ArrayList<>();
        private final List<OutcomeGuard> guards = new ArrayList<>();
        private final List<ServiceAction> actions = new ArrayList<>();

        private Builder(EasyEntityQuery db) { this.db = Objects.requireNonNull(db, "db"); }

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

        /** 工作日历：时限写成工作日时按它算；默认周一到周五、UTC 的日期（{@link WorkCalendar#weekdays}） */
        public Builder<U> workCalendar(WorkCalendar calendar) { this.calendar = calendar; return this; }

        /** 注册一个处理人解析器（定义里的 {@code { "resolver": 名字 }}）；同名的后注册的换掉先注册的 */
        public Builder<U> resolver(PeopleResolver resolver) { this.resolvers.add(resolver); return this; }

        /** 注册一个提交前的守卫（处理结果的 {@code guards}） */
        public Builder<U> guard(OutcomeGuard guard) { this.guards.add(guard); return this; }

        /** 注册一个自动节点的动作（{@code service} 节点的 {@code action}） */
        public Builder<U> action(ServiceAction action) { this.actions.add(action); return this; }

        /** 注册一个命令拦截器：审计、业务校验、改参数、换掉某个命令的标准行为（{@link CommandInterceptor}）；先注册的在外层 */
        public Builder<U> interceptor(CommandInterceptor<U> interceptor) { this.interceptors.add(interceptor); return this; }

        /** 注册一个监听：发通知、同步业务表、审计（{@link FlowListener}） */
        public Builder<U> listener(FlowListener listener) { this.listeners.add(listener); return this; }

        /** 注册一个通知的渠道（站内信、邮件……，{@link NotificationChannel}）：待办到达、催办、到期、超时、抄送、被退回、流程结束时发 */
        public Builder<U> channel(NotificationChannel channel) { this.channels.add(channel); return this; }

        /** 提交后的监听怎么排：默认引擎的事务做完马上调；命令会加入外面的事务时换成那个事务的提交后回调 */
        public Builder<U> afterCommit(AfterCommit afterCommit) { this.afterCommit = afterCommit; return this; }

        /** 注册一种作业的处理（新的种类，或换掉内置的 timer、overdue、remind、unclaimed） */
        public Builder<U> jobHandler(JobHandler handler) { this.jobHandlers.add(handler); return this; }

        /** 组装 */
        public FlowEngine<U> build() {
            if (org == null) throw new IllegalStateException("an OrgDirectory is required: the engine has no default organisation");
            // 工作进程替所有租户做作业：做每个作业时切到它的租户（TenantScope），其余时候是接入方给的当前租户
            FlowEasyQuery.install(db, TenantScope.over(tenant), clock);
            return new FlowEngine<>(new EngineContext(db, clock, org, calendar, new EventDispatcher(List.copyOf(listeners), List.copyOf(channels), afterCommit),
                new Extensions(resolvers, guards, actions), new DefinitionCache(db)), actors, permissions, List.copyOf(interceptors),
                List.copyOf(jobHandlers));
        }
    }
}
