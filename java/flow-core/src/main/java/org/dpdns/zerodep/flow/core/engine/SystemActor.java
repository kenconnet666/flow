package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.identity.Actor;

import java.util.Set;

/**
 * 引擎自己推进时的"操作人"（定时节点到点、超时自动处理）：没有编号也没有名字，流转记录上的操作人是空的（前端显示"系统"）。
 */
public class SystemActor implements Actor {
    public static final SystemActor INSTANCE = new SystemActor();

    private SystemActor() { }

    @Override public Long getId() { return null; }

    @Override public String getName() { return null; }

    @Override public Set<Long> getDepartments() { return Set.of(); }

    @Override public Set<Long> getRoles() { return Set.of(); }
}
