package org.dpdns.zerodep.flow.it.support;

import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.command.CommandInterceptor;
import org.dpdns.zerodep.flow.core.command.CommentCommand;
import org.dpdns.zerodep.flow.core.command.CompleteCommand;
import org.dpdns.zerodep.flow.core.command.FlowCommand;
import org.dpdns.zerodep.flow.core.command.StartCommand;
import org.dpdns.zerodep.flow.core.extension.OutcomeGuard;
import org.dpdns.zerodep.flow.core.extension.PeopleResolver;
import org.dpdns.zerodep.flow.core.extension.ServiceAction;
import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.model.error.ErrorDetail;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 场景里用的扩展（spec/scenarios 的 README 有一张表）：接入方就是这样按名字注册解析器、守卫、动作的。
 * <ul>
 *   <li>解析器 {@code manager}：谁发起都由总经理（1004）处理；</li>
 *   <li>守卫 {@code budget}：表单的金额超过 100000 时不通过，原因"超出预算"；</li>
 *   <li>动作 {@code classify}：按参数 amount 分大小，≥ 10000 走 {@code big}，否则 {@code small}，并记流程变量 level；</li>
 *   <li>动作 {@code archive}：同一个去重键的前 {@code failures} 次失败（演示重试），之后成功；</li>
 *   <li>动作 {@code explode}：总是失败（演示同步回滚与异步重试用完）；</li>
 *   <li>命令拦截器：意见是"拦下"的处理拦下（{@code comment: blocked}）；留言里的"敏感词"换成"***"（改参数）；
 *       业务编号是 {@code rollback-after} 的发起做完之后再拦（{@code businessKey: rolled-back}），整个回滚。</li>
 * </ul>
 * 每个场景一份（失败的次数按场景计）。
 */
public class TestExtensions {
    /** 去重键 → 已经失败了几次 */
    private final Map<String, Integer> failures = new HashMap<>();

    /** 注册到引擎上 */
    public <U extends Actor> FlowEngine.Builder<U> register(FlowEngine.Builder<U> builder) {
        return builder
            .resolver(resolver("manager", context -> List.of(1004L)))
            .guard(guard("budget", context -> new BigDecimal(String.valueOf(context.getForm().getOrDefault("amount", "0")))
                .compareTo(new BigDecimal("100000")) > 0 ? "超出预算" : null))
            .action(action("classify", context -> {
                boolean big = new BigDecimal(String.valueOf(context.getArgs().get("amount"))).compareTo(new BigDecimal("10000")) >= 0;
                return new ServiceAction.Result(big ? "big" : "small", Map.of("level", big ? "big" : "small"));
            }))
            .action(action("archive", context -> {
                int allowed = ((Number) context.getArgs().getOrDefault("failures", 0)).intValue();
                int failed = failures.getOrDefault(context.getIdempotencyKey(), 0);
                if (failed < allowed) {
                    failures.put(context.getIdempotencyKey(), failed + 1);
                    throw new IllegalStateException("档案系统暂时不可用");
                }
                return new ServiceAction.Result(null, Map.of("archived", true));
            }))
            .action(action("explode", context -> {
                throw new IllegalStateException("ERP 不可用");
            }))
            .interceptor(new CommandInterceptor<U>() {
                @Override
                public <R> R intercept(U actor, String operation, FlowCommand command, Chain<R> chain) {
                    if (command instanceof CompleteCommand complete && "拦下".equals(complete.getComment())) throw invalid("comment", "blocked");
                    if (command instanceof CommentCommand comment && comment.getText() != null) {
                        return chain.proceed(new CommentCommand(comment.getInstanceId(), comment.getText().replace("敏感词", "***")));
                    }
                    R result = chain.proceed(command);
                    if (command instanceof StartCommand start && "rollback-after".equals(start.getBusinessKey())) throw invalid("businessKey", "rolled-back");
                    return result;
                }
            });
    }

    private static FlowException invalid(String field, String code) {
        return new FlowException(FlowErrorCode.VALIDATION_FAILED, field + ": " + code, List.of(ErrorDetail.field(field, code)));
    }

    private static PeopleResolver resolver(String name, Function<PeopleResolver.Context, List<Long>> fn) {
        return new PeopleResolver() {
            @Override public String name() { return name; }

            @Override public List<Long> resolve(Context context) { return fn.apply(context); }
        };
    }

    private static OutcomeGuard guard(String name, Function<OutcomeGuard.Context, String> fn) {
        return new OutcomeGuard() {
            @Override public String name() { return name; }

            @Override public String check(Context context) { return fn.apply(context); }
        };
    }

    private static ServiceAction action(String name, Function<ServiceAction.Context, ServiceAction.Result> fn) {
        return new ServiceAction() {
            @Override public String name() { return name; }

            @Override public Result perform(Context context) { return fn.apply(context); }
        };
    }
}
