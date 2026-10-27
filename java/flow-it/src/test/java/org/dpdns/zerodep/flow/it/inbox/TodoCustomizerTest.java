package org.dpdns.zerodep.flow.it.inbox;

import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.command.CompleteCommand;
import org.dpdns.zerodep.flow.core.command.StartCommand;
import org.dpdns.zerodep.flow.core.engine.query.ListFilter;
import org.dpdns.zerodep.flow.core.engine.state.Variables;
import org.dpdns.zerodep.flow.core.entity.proxy.WorkItemEntityProxy;
import org.dpdns.zerodep.flow.core.spi.Actor;
import org.dpdns.zerodep.flow.core.spi.FormValues;
import org.dpdns.zerodep.flow.core.spi.TodoQueryCustomizer;
import org.dpdns.zerodep.flow.core.view.Page;
import org.dpdns.zerodep.flow.core.view.workitem.TodoItem;
import org.dpdns.zerodep.flow.it.support.LiveMysql;
import org.dpdns.zerodep.flow.it.support.TestOrg;
import org.dpdns.zerodep.flow.it.support.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 待办列表连接入方的表（TodoQueryCustomizer）：这里用业务单号代替接入方的表，按单号前缀筛、补一列单号 */
@DisplayName("待办列表的接入方条件与业务列")
class TodoCustomizerTest {
    private static final TestUser ALICE = TestUser.of(1001L, "林小满");
    private static final TestUser BOB = TestUser.of(1002L, "周启航");

    private static final String PROCESS = """
        { "key": "memo", "name": "备忘", "title": "备忘",
          "nodes": [ { "id": "start", "type": "start", "name": "提交" },
                     { "id": "check", "type": "task", "name": "审核", "assignees": [{ "user": 1002 }], "outcomes": [{ "key": "ok", "label": "同意" }] },
                     { "id": "end", "type": "end", "name": "结束", "result": "done" } ],
          "edges": [ { "id": "e1", "from": "start", "to": "check" }, { "id": "e2", "from": "check", "to": "end", "outcome": "ok" } ] }
        """;

    /** 参数 keyPrefix 按业务单号前缀筛；业务列 orderNo 是单号（接入方真实的做法是 exists 子查询连自己的表、按编号批量查） */
    private static final TodoQueryCustomizer BY_ORDER = new TodoQueryCustomizer() {
        @Override
        public void filter(Actor actor, Map<String, String> params, WorkItemEntityProxy item) {
            String prefix = params.get("keyPrefix");
            if (prefix != null) item.instance().businessKey().startsWith(prefix);
        }

        @Override
        public Map<Long, Map<String, Object>> columns(Actor actor, List<TodoItem> rows) {
            Map<Long, Map<String, Object>> out = new LinkedHashMap<>();
            for (TodoItem row : rows) {
                if (row.getInstance() != null && row.getInstance().getBusinessKey() != null) out.put(row.getId(), Map.of("orderNo", row.getInstance().getBusinessKey()));
            }
            return out;
        }
    };

    private FlowEngine<TestUser> engine;

    @BeforeEach
    void setUp() {
        LiveMysql.recreateTables();
        engine = FlowEngine.<TestUser>builder(LiveMysql.easyQuery()).orgDirectory(new TestOrg(List.of(ALICE, BOB))).todoQueryCustomizer(BY_ORDER).build();
        engine.publishDefinition(ALICE, PROCESS, null);
    }

    @Test
    void 按接入方的参数筛_每行补业务列() {
        engine.start(ALICE, new StartCommand("memo", FormValues.empty(), Variables.empty(), "PO-1", null, 0, Map.of()));
        var sales = engine.start(ALICE, new StartCommand("memo", FormValues.empty(), Variables.empty(), "SO-2", null, 0, Map.of()));
        engine.start(ALICE, StartCommand.of("memo", Map.of()));

        assertEquals(3, engine.todo(BOB, 1, 20).getTotal());
        Page<TodoItem> orders = engine.todo(BOB, ListFilter.builder().business(Map.of("keyPrefix", "PO-")).build(), 1, 20);
        assertEquals(1, orders.getTotal());
        assertEquals(Map.of("orderNo", "PO-1"), orders.getRows().get(0).getBusiness());
        assertNull(engine.todo(BOB, 1, 20).getRows().stream().filter(r -> r.getInstance().getBusinessKey() == null).findFirst().orElseThrow().getBusiness(),
            "没有单号的行不补");

        engine.complete(BOB, CompleteCommand.of(sales.getCreated().get(0).getId(), "ok"));
        Page<TodoItem> done = engine.done(BOB, ListFilter.builder().business(Map.of("keyPrefix", "SO-")).build(), 1, 20);
        assertEquals(1, done.getTotal());
        assertEquals(Map.of("orderNo", "SO-2"), done.getRows().get(0).getBusiness());
    }
}
