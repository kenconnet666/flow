package org.dpdns.zerodep.flow.it.admin;

import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.command.CompleteCommand;
import org.dpdns.zerodep.flow.core.command.MigrateCommand;
import org.dpdns.zerodep.flow.core.command.StartCommand;
import org.dpdns.zerodep.flow.core.view.instance.ProgressView;
import org.dpdns.zerodep.flow.core.view.stats.HandlerStats;
import org.dpdns.zerodep.flow.core.view.stats.NodeStats;
import org.dpdns.zerodep.flow.core.view.stats.ProcessStats;
import org.dpdns.zerodep.flow.it.support.LiveMysql;
import org.dpdns.zerodep.flow.it.support.TestOrg;
import org.dpdns.zerodep.flow.it.support.TestUser;
import org.dpdns.zerodep.flow.model.error.FlowException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 管理与运维：实例迁移到新版本、外部参与方看的进度、统计 */
@DisplayName("迁移、进度、统计")
class AdminOpsTest {
    private static final TestUser ALICE = TestUser.of(1001L, "林小满");
    private static final TestUser BOB = TestUser.of(1002L, "周启航");
    private static final TestUser ADMIN = TestUser.of(1000L, "管理员");

    /** 第 1 版：审核叫 check；第 2 版改名 review，加了一个 notify 抄送 */
    private static final String V1 = """
        { "key": "memo", "name": "备忘", "title": "备忘",
          "nodes": [ { "id": "start", "type": "start", "name": "提交" },
                     { "id": "check", "type": "task", "name": "审核", "assignees": [{ "user": 1002 }], "outcomes": [{ "key": "ok", "label": "同意" }] },
                     { "id": "end", "type": "end", "name": "结束", "result": "done" } ],
          "edges": [ { "id": "e1", "from": "start", "to": "check" }, { "id": "e2", "from": "check", "to": "end", "outcome": "ok" } ] }
        """;
    private static final String V2 = """
        { "key": "memo", "name": "备忘", "title": "备忘",
          "nodes": [ { "id": "start", "type": "start", "name": "提交" },
                     { "id": "review", "type": "task", "name": "复核", "assignees": [{ "user": 1002 }], "outcomes": [{ "key": "ok", "label": "同意" }] },
                     { "id": "end", "type": "end", "name": "结束", "result": "done" } ],
          "edges": [ { "id": "e1", "from": "start", "to": "review" }, { "id": "e2", "from": "review", "to": "end", "outcome": "ok" } ] }
        """;

    private FlowEngine<TestUser> engine;

    @BeforeEach
    void setUp() {
        LiveMysql.recreateTables();
        engine = FlowEngine.<TestUser>builder(LiveMysql.easyQuery()).orgDirectory(new TestOrg(List.of(ALICE, BOB, ADMIN))).build();
    }

    @Test
    void 迁移到新版本_进度_统计() {
        engine.publishDefinition(ADMIN, V1, null);
        var started = engine.start(ALICE, StartCommand.of("memo", Map.of()));
        Long instanceId = started.getInstance().getId();
        engine.publishDefinition(ADMIN, V2, null);

        ProgressView progress = engine.progress(ALICE, instanceId);
        assertEquals("审核", progress.getSteps().get(0).getNodeName());
        assertEquals("周启航", progress.getSteps().get(0).getHandlerName());

        // 新版本里没有 check：不写对应关系不能迁
        assertThrows(FlowException.class, () -> engine.migrate(ADMIN, new MigrateCommand(instanceId, 2, Map.of(), "换新版")));
        var migrated = engine.migrate(ADMIN, new MigrateCommand(instanceId, 2, Map.of("check", "review"), "换新版"));
        assertEquals(2, migrated.getInstance().getVersion());
        assertEquals("instance.migrated", migrated.getEvents().get(0).getType());
        assertEquals("复核", engine.progress(ALICE, instanceId).getSteps().get(0).getNodeName());

        var done = engine.complete(BOB, CompleteCommand.of(started.getCreated().get(0).getId(), "ok"));
        assertEquals("completed", done.getInstance().getStatus());

        ProcessStats stats = engine.processStats(ADMIN).get(0);
        assertEquals("memo", stats.getProcessKey());
        assertEquals(1, stats.getCompleted());
        assertEquals(0, stats.getRunning());
        NodeStats node = engine.nodeStats(ADMIN, "memo", Instant.EPOCH).get(0);
        assertEquals("review", node.getNode());
        assertEquals(1, node.getDone());
        HandlerStats handler = engine.handlerStats(ADMIN, Instant.EPOCH).get(0);
        assertEquals(1002L, handler.getUserId());
        assertEquals(1, handler.getDone());
    }
}
