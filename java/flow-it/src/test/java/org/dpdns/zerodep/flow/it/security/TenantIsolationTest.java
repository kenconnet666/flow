package org.dpdns.zerodep.flow.it.security;

import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.command.ClaimCommand;
import org.dpdns.zerodep.flow.core.command.CompleteCommand;
import org.dpdns.zerodep.flow.core.command.StartCommand;
import org.dpdns.zerodep.flow.core.engine.query.InstanceQueries.InstanceSearch;
import org.dpdns.zerodep.flow.core.engine.query.SortOrder;
import org.dpdns.zerodep.flow.it.support.LiveMysql;
import org.dpdns.zerodep.flow.it.support.TestOrg;
import org.dpdns.zerodep.flow.it.support.TestUser;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 多租户：同一个引擎、同一个库，换了当前租户就看不到、动不了别的租户的流程、实例、待办（拦截器加的 tenant_id 条件）；
 * 归档是例外，替所有租户搬，搬完各租户仍只看得到自己的。
 */
@DisplayName("多租户隔离")
class TenantIsolationTest {
    private static final TestUser ALICE = TestUser.of(1001L, "林小满");
    private static final TestUser BOB = TestUser.of(1002L, "周启航");

    private static final String PROCESS = """
        { "key": "memo", "name": "备忘", "title": "备忘",
          "nodes": [ { "id": "start", "type": "start", "name": "提交" },
                     { "id": "check", "type": "task", "name": "审核", "assignees": [{ "user": 1002 }], "outcomes": [{ "key": "ok", "label": "同意" }] },
                     { "id": "end", "type": "end", "name": "结束", "result": "done" } ],
          "edges": [ { "id": "e1", "from": "start", "to": "check" }, { "id": "e2", "from": "check", "to": "end", "outcome": "ok" } ] }
        """;
    private static final InstanceSearch ALL = new InstanceSearch(null, null, null, null, SortOrder.NONE);

    /** 当前租户：接入方一般从请求里取，这里直接切 */
    private final AtomicLong tenant = new AtomicLong(1);
    private FlowEngine<TestUser> engine;

    @BeforeEach
    void setUp() {
        LiveMysql.recreateTables();
        engine = FlowEngine.<TestUser>builder(LiveMysql.easyQuery()).orgDirectory(new TestOrg(List.of(ALICE, BOB))).tenant(tenant::get).build();
    }

    @Test
    void 别的租户的流程_实例_待办都看不到也动不了() {
        engine.publishDefinition(ALICE, PROCESS, null);
        var started = engine.start(ALICE, StartCommand.of("memo", Map.of()));
        Long instanceId = started.getInstance().getId();
        Long itemId = started.getCreated().get(0).getId();

        tenant.set(2);
        assertNotFound(() -> engine.start(ALICE, StartCommand.of("memo", Map.of())), "流程只在租户 1 发布过");
        assertEquals(0, engine.todo(BOB, 1, 20).getTotal());
        assertEquals(0, engine.started(ALICE, 1, 20).getTotal());
        assertEquals(0, engine.instances(BOB, ALL, 1, 20).getTotal());
        assertNotFound(() -> engine.instance(ALICE, instanceId), "按编号也打不开");
        assertNotFound(() -> engine.workItem(BOB, itemId), "待办也一样");
        assertThrows(FlowException.class, () -> engine.claim(BOB, new ClaimCommand(itemId)));
        assertThrows(FlowException.class, () -> engine.complete(BOB, CompleteCommand.of(itemId, "ok")));

        engine.publishDefinition(ALICE, PROCESS, null);   // 同一个流程标识在另一个租户里各自一份
        engine.start(ALICE, StartCommand.of("memo", Map.of()));
        assertEquals(1, engine.started(ALICE, 1, 20).getTotal());

        tenant.set(1);
        assertEquals(1, engine.todo(BOB, 1, 20).getTotal(), "租户 1 的待办没被租户 2 动过");
        assertEquals("open", engine.workItem(BOB, itemId).getWorkItem().getState());
    }

    @Test
    void 归档替所有租户搬_搬完各自只看得到自己的() {
        engine.publishDefinition(ALICE, PROCESS, null);
        var first = engine.start(ALICE, StartCommand.of("memo", Map.of()));
        engine.complete(BOB, CompleteCommand.of(first.getCreated().get(0).getId(), "ok"));
        tenant.set(2);
        engine.publishDefinition(ALICE, PROCESS, null);
        var second = engine.start(ALICE, StartCommand.of("memo", Map.of()));
        engine.complete(BOB, CompleteCommand.of(second.getCreated().get(0).getId(), "ok"));

        assertEquals(2, engine.archive(Duration.ZERO), "在租户 2 里调，也搬了租户 1 的");

        assertEquals(second.getInstance().getId(), engine.instance(ALICE, second.getInstance().getId()).getInstance().getId());
        assertNotFound(() -> engine.instance(ALICE, first.getInstance().getId()), "归档表里也按租户隔开");
        tenant.set(1);
        assertEquals("completed", engine.instance(ALICE, first.getInstance().getId()).getInstance().getStatus());
    }

    private static void assertNotFound(Executable call, String message) {
        assertEquals(FlowErrorCode.NOT_FOUND, assertThrows(FlowException.class, call, message).getCode(), message);
    }
}
