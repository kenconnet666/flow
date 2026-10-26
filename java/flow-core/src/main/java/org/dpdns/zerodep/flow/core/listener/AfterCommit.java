package org.dpdns.zerodep.flow.core.listener;

/**
 * "提交之后"怎么排：引擎的命令做完时，把提交后的监听交给它。默认 {@link #immediately}——命令的事务是引擎自己开的，
 * 做完就已经提交了。命令加入了接入方外面的事务时，换成那个事务框架的"提交后回调"（Spring 适配里用
 * {@code TransactionSynchronization}），外面的事务回滚了就不发。
 */
@FunctionalInterface
public interface AfterCommit {
    /** 在提交之后做 task；没有外面的事务时马上做 */
    void run(Runnable task);

    /** 马上做 */
    static AfterCommit immediately() { return Runnable::run; }
}
