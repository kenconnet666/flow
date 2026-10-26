package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.calendar.WorkCalendar;
import org.dpdns.zerodep.flow.core.extension.Extensions;
import org.dpdns.zerodep.flow.core.identity.OrgDirectory;
import org.dpdns.zerodep.flow.core.store.Stores;
import org.dpdns.zerodep.query.exec.Database;

import java.time.Clock;
import java.util.function.Supplier;

/**
 * 引擎内部各处理类共用的东西，由 {@code FlowEngine.Builder} 组装一次。
 *
 * @param db     命令的事务在它上面开（外面已有同一个数据源的事务时加入）
 * @param stores 六个存储
 * @param clock  时钟（UTC）
 * @param org    接入方的组织架构
 * @param calendar 工作日历：时限写成工作日时按它算
 * @param dispatcher 把写下的流转记录交给监听，管着引擎的事务边界
 * @param extensions 接入方按名字注册的解析器、守卫、动作
 */
public record EngineContext(Database db, Stores stores, Clock clock, OrgDirectory org, WorkCalendar calendar, EventDispatcher dispatcher,
                            Extensions extensions) {
    /** 在引擎的事务里做（外面已有同一个数据源的事务时加入）；最外层做完时发提交后的监听 */
    public <T> T transaction(Supplier<T> work) { return dispatcher.transaction(db, work); }

    /** 同上，没有返回值 */
    public void transaction(Runnable work) {
        dispatcher.transaction(db, () -> {
            work.run();
            return null;
        });
    }
}
