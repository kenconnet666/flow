package org.dpdns.zerodep.flow.it.admin;

import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.command.CcCommand;
import org.dpdns.zerodep.flow.core.command.CompleteCommand;
import org.dpdns.zerodep.flow.core.command.StartCommand;
import org.dpdns.zerodep.flow.core.engine.query.ListFilter;
import org.dpdns.zerodep.flow.core.view.instance.InstanceDetail;
import org.dpdns.zerodep.flow.it.support.LiveMysql;
import org.dpdns.zerodep.flow.it.support.TestOrg;
import org.dpdns.zerodep.flow.it.support.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 归档（0007 第 6 节）：结束的实例搬进 *_archive 之后，详情照样打开，我已办、我发起的、抄送我的带 archived 看得到 */
@DisplayName("归档")
class ArchiveTest {
    private static final TestUser ALICE = TestUser.of(1001L, "林小满");
    private static final TestUser BOB = TestUser.of(1002L, "周启航");

    private static final String PROCESS = """
        { "key": "memo", "name": "备忘", "title": "备忘",
          "nodes": [ { "id": "start", "type": "start", "name": "提交" },
                     { "id": "check", "type": "task", "name": "审核", "assignees": [{ "user": 1002 }], "outcomes": [{ "key": "ok", "label": "同意" }] },
                     { "id": "end", "type": "end", "name": "结束", "result": "done" } ],
          "edges": [ { "id": "e1", "from": "start", "to": "check" }, { "id": "e2", "from": "check", "to": "end", "outcome": "ok" } ] }
        """;

    private FlowEngine<TestUser> engine;

    @BeforeEach
    void setUp() {
        LiveMysql.recreateTables();
        engine = FlowEngine.<TestUser>builder(LiveMysql.easyQuery()).orgDirectory(new TestOrg(List.of(ALICE, BOB))).build();
        engine.publishDefinition(ALICE, PROCESS, null);
    }

    @Test
    void 结束的实例搬进归档表_详情照样打开_列表带archived看() {
        var ended = engine.start(ALICE, StartCommand.of("memo", Map.of()));
        engine.cc(BOB, new CcCommand(ended.getInstance().getId(), List.of(1001L), "看一下"));
        engine.complete(BOB, CompleteCommand.of(ended.getCreated().get(0).getId(), "ok"));
        engine.start(ALICE, StartCommand.of("memo", Map.of()));
        Long id = ended.getInstance().getId();

        assertEquals(1, engine.archive(Duration.ZERO), "只搬结束了的");
        assertEquals(0, engine.archive(Duration.ZERO), "搬过的不再搬");

        InstanceDetail detail = engine.instance(ALICE, id);
        assertEquals("completed", detail.getInstance().getStatus());
        assertEquals(1, detail.getWorkItems().size());
        assertEquals("instance.started", detail.getTimeline().get(0).getType());

        assertEquals(1, engine.started(ALICE, 1, 20).getTotal(), "热表里只剩进行中的");
        var archivedStarted = engine.started(ALICE, ListFilter.builder().archived(true).build(), 1, 20);
        assertEquals(1, archivedStarted.getTotal());
        assertEquals(id, archivedStarted.getRows().get(0).getId());

        assertEquals(0, engine.done(BOB, 1, 20).getTotal());
        var archivedDone = engine.done(BOB, ListFilter.builder().archived(true).build(), 1, 20);
        assertEquals(1, archivedDone.getTotal());
        assertNotNull(archivedDone.getRows().get(0).getInstance(), "归档的待办也带上实例");
        assertEquals("同意", archivedDone.getRows().get(0).getOutcomeLabel());

        assertEquals(0, engine.ccList(ALICE, ListFilter.NONE, 1, 20).getTotal());
        var archivedCc = engine.ccList(ALICE, ListFilter.builder().archived(true).processKey("memo").build(), 1, 20);
        assertEquals(1, archivedCc.getTotal(), "归档的抄送按流程筛走子查询");
        assertNotNull(archivedCc.getRows().get(0).getInstance());
        assertEquals(0, engine.ccList(ALICE, ListFilter.builder().archived(true).processKey("other").build(), 1, 20).getTotal());
    }
}
