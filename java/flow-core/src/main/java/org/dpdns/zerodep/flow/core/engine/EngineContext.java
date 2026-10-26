package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.calendar.WorkCalendar;
import org.dpdns.zerodep.flow.core.identity.OrgDirectory;
import org.dpdns.zerodep.flow.core.store.Stores;
import org.dpdns.zerodep.query.exec.Database;

import java.time.Clock;

/**
 * 引擎内部各处理类共用的东西，由 {@code FlowEngine.Builder} 组装一次。
 *
 * @param db     命令的事务在它上面开（外面已有同一个数据源的事务时加入）
 * @param stores 六个存储
 * @param clock  时钟（UTC）
 * @param org    接入方的组织架构
 * @param calendar 工作日历：时限写成工作日时按它算
 */
public record EngineContext(Database db, Stores stores, Clock clock, OrgDirectory org, WorkCalendar calendar) { }
