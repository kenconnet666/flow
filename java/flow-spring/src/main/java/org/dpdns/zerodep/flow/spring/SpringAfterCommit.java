package org.dpdns.zerodep.flow.spring;

import org.dpdns.zerodep.flow.core.spi.AfterCommit;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 提交后的监听在 Spring 里怎么排：命令加入了应用外面的事务（{@code @Transactional} 的方法里调引擎）时，等那个事务提交了再调，
 * 回滚了就不调；没有外面的事务时（引擎自己的事务已经提交）马上调。
 */
public class SpringAfterCommit implements AfterCommit {
    @Override
    public void run(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() { task.run(); }
        });
    }
}
