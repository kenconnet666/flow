package org.dpdns.zerodep.flow.core.engine;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import lombok.Data;
import org.dpdns.zerodep.flow.core.calendar.WorkCalendar;
import org.dpdns.zerodep.flow.core.extension.Extensions;
import org.dpdns.zerodep.flow.core.identity.OrgDirectory;

import java.time.Clock;
import java.util.function.Supplier;

/** 引擎内部各处理类共用的东西，由 {@code FlowEngineBuilder} 组装一次。 */
@Data
public class EngineContext {
    /** 接入方的 easy-query（装好了引擎的表要的转换器与拦截器）；命令的事务在它上面开，外面已有事务时加入 */
    private final EasyEntityQuery db;
    /** 时钟（UTC） */
    private final Clock clock;
    /** 接入方的组织架构 */
    private final OrgDirectory org;
    /** 工作日历：时限写成工作日时按它算 */
    private final WorkCalendar calendar;
    /** 把写下的流转记录交给监听，管着引擎的事务边界 */
    private final EventDispatcher dispatcher;
    /** 接入方按名字注册的解析器、守卫、动作 */
    private final Extensions extensions;
    /** 发布的版本，解析一次就记住 */
    private final DefinitionCache definitions;

    /** 在引擎的事务里做（外面已有事务时加入）；最外层做完时发提交后的监听 */
    public <T> T transaction(Supplier<T> work) { return dispatcher.transaction(db, work); }

    /** 同上，没有返回值 */
    public void transaction(Runnable work) {
        dispatcher.transaction(db, () -> {
            work.run();
            return null;
        });
    }
}
