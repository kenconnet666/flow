package org.dpdns.zerodep.flow.core.command;

import org.dpdns.zerodep.flow.core.identity.Actor;

/**
 * 命令拦截器（0006 第 6 节"管道"）：每个命令在权限检查之后经过它，和命令的标准处理在同一个事务里——
 * 用来做审计、业务校验、改参数，或者整个换掉某个命令的标准行为（不调 {@link Chain#proceed}，自己做）。
 * 几个拦截器按注册的先后套起来，先注册的在外层。
 * <pre>{@code
 * builder.interceptor(new CommandInterceptor<MyUser>() {
 *     public <R> R intercept(MyUser actor, String operation, FlowCommand command, Chain<R> chain) {
 *         if (command instanceof CompleteCommand c && blocked(c)) throw new FlowException(...);   // 业务校验：拦下
 *         R result = chain.proceed(command);                                                      // 照常做（可以换成改过参数的同类型命令）
 *         audit.record(actor, operation, command);                                                // 审计：抛异常时整个命令回滚
 *         return result;
 *     }
 * });
 * }</pre>
 *
 * @param <U> 接入方的用户类型（同 {@code FlowEngine<U>}）
 */
public interface CommandInterceptor<U extends Actor> {
    /**
     * 拦一次命令。
     *
     * @param actor     操作人
     * @param operation 命令的名字，同 {@code PermissionPolicy} 的 operation（{@code complete}、{@code transfer}……）
     * @param command   命令
     * @param chain     往下：后面的拦截器，最后是命令的标准处理
     * @return 命令的结果（多半是 {@link CommandResult}；抄送已读、失败的作业重试等是它们自己的类型），原样或换过的
     */
    <R> R intercept(U actor, String operation, FlowCommand command, Chain<R> chain);

    /** 往下做 */
    @FunctionalInterface
    interface Chain<R> {
        /** 做这个命令：原来的，或改过参数的同类型命令 */
        R proceed(FlowCommand command);
    }
}
