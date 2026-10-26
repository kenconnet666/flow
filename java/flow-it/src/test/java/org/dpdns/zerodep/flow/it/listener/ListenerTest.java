package org.dpdns.zerodep.flow.it.listener;

import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.command.CompleteCommand;
import org.dpdns.zerodep.flow.core.command.StartCommand;
import org.dpdns.zerodep.flow.core.listener.FlowListener;
import org.dpdns.zerodep.flow.core.listener.FlowNotice;
import org.dpdns.zerodep.flow.it.support.LiveMysql;
import org.dpdns.zerodep.flow.it.support.TestOrg;
import org.dpdns.zerodep.flow.it.support.TestUser;
import org.dpdns.zerodep.flow.model.runtime.FlowEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 监听（0006 第 5 节）：提交后的拿到这一步的流转记录与节点的 meta；事务内的抛错让整个命令回滚 */
@DisplayName("监听")
class ListenerTest {
    private static final TestUser ALICE = TestUser.of(1001L, "林小满");
    private static final TestUser BOSS = TestUser.of(1004L, "总经理");

    private static final String PROCESS = """
        { "key": "memo", "name": "备忘", "title": "备忘",
          "nodes": [
            { "id": "start", "type": "start", "name": "提交" },
            { "id": "read", "type": "task", "name": "阅", "assignees": [{ "user": 1004 }], "meta": { "channel": "mail" },
              "outcomes": [{ "key": "ok", "label": "已阅" }] },
            { "id": "end", "type": "end", "name": "结束", "result": "read" } ],
          "edges": [
            { "id": "e1", "from": "start", "to": "read" },
            { "id": "e2", "from": "read", "to": "end", "outcome": "ok" } ] }
        """;

    private final List<FlowNotice> delivered = new ArrayList<>();

    @BeforeEach
    void freshTables() {
        LiveMysql.recreateTables();
    }

    private FlowEngine<TestUser> engine(FlowListener... more) {
        FlowEngine.Builder<TestUser> builder = FlowEngine.<TestUser>builder(LiveMysql.dataSource())
            .orgDirectory(new TestOrg(List.of(ALICE, BOSS)))
            .listener(delivered::add);
        for (FlowListener listener : more) builder.listener(listener);
        FlowEngine<TestUser> engine = builder.build();
        engine.publishDefinition(ALICE, PROCESS, null);
        return engine;
    }

    @Test
    void 提交后拿到这一步的流转记录与节点的meta() {
        FlowEngine<TestUser> engine = engine();
        var started = engine.start(ALICE, StartCommand.of("memo", Map.of()));
        assertEquals(List.of(FlowEvent.INSTANCE_STARTED, FlowEvent.NODE_LEFT, FlowEvent.NODE_ENTERED, FlowEvent.WORK_ITEM_CREATED), types());
        FlowNotice created = delivered.get(3);
        assertEquals("mail", created.nodeMeta().get("channel"));
        assertEquals("memo", created.instance().processKey());
        assertTrue(created.event().id() != null, "通知里的流转记录带数据库给的编号");

        delivered.clear();
        engine.complete(BOSS, CompleteCommand.of(started.created().get(0).id(), "ok"));
        assertEquals(FlowEvent.INSTANCE_FINISHED, types().get(types().size() - 1));
    }

    @Test
    void 事务内的监听抛错整个命令回滚_提交后的不发() {
        FlowListener veto = new FlowListener() {
            @Override public void on(FlowNotice notice) { throw new IllegalStateException("预算系统不同意"); }

            @Override public Phase phase() { return Phase.IN_TRANSACTION; }

            @Override public Set<String> types() { return Set.of(FlowEvent.WORK_ITEM_CREATED); }
        };
        FlowEngine<TestUser> engine = engine(veto);
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> engine.start(ALICE, StartCommand.of("memo", Map.of())));
        assertEquals("预算系统不同意", error.getMessage());
        assertEquals(List.of(), types(), "回滚了，提交后的监听什么也没收到");
        assertEquals(0L, engine.started(ALICE, 1, 10).total(), "实例没留下");
    }

    private List<String> types() {
        return delivered.stream().map(n -> n.event().type()).toList();
    }
}
