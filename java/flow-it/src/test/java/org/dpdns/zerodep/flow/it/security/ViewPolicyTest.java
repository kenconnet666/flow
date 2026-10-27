package org.dpdns.zerodep.flow.it.security;

import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.command.CcCommand;
import org.dpdns.zerodep.flow.core.command.ClaimCommand;
import org.dpdns.zerodep.flow.core.command.CompleteCommand;
import org.dpdns.zerodep.flow.core.command.StartCommand;
import org.dpdns.zerodep.flow.core.spi.PermissionPolicy;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 谁能看实例（ViewPolicy 的默认规则）：参与过的人总能看；别人要有管理端搜实例（instances.search）的权限。
 * 权限默认全部放行，所以不配权限时谁都能看；这里把 instances.search 只给管理员，看没参与过的人被挡住。
 */
@DisplayName("谁能看实例")
class ViewPolicyTest {
    private static final TestUser ALICE = TestUser.of(1001L, "林小满");
    private static final TestUser BOB = TestUser.of(1002L, "周启航");
    private static final TestUser CAROL = TestUser.of(1003L, "沈知远", 7L);
    private static final TestUser ERIN = TestUser.of(1005L, "陆青禾");
    private static final TestUser DAVE = TestUser.of(1004L, "旁观者");
    private static final TestUser ADMIN = TestUser.of(1000L, "管理员");
    private static final List<TestUser> EVERYONE = List.of(ALICE, BOB, CAROL, ERIN, DAVE, ADMIN);

    private static final String PROCESS = """
        { "key": "memo", "name": "备忘", "title": "备忘",
          "nodes": [ { "id": "start", "type": "start", "name": "提交" },
                     { "id": "review", "type": "task", "name": "复核", "assignees": [{ "role": 7 }], "outcomes": [{ "key": "ok", "label": "通过" }] },
                     { "id": "approve", "type": "task", "name": "审批", "assignees": [{ "user": 1002 }], "outcomes": [{ "key": "ok", "label": "同意" }] },
                     { "id": "end", "type": "end", "name": "结束", "result": "done" } ],
          "edges": [ { "id": "e1", "from": "start", "to": "review" }, { "id": "e2", "from": "review", "to": "approve", "outcome": "ok" },
                     { "id": "e3", "from": "approve", "to": "end", "outcome": "ok" } ] }
        """;

    /** instances.search 只给管理员，别的都放行 */
    private static final PermissionPolicy<TestUser> ADMIN_SEARCH = (actor, operation, target) ->
        !operation.equals("instances.search") || actor.getId().equals(ADMIN.getId());

    @BeforeEach
    void setUp() {
        LiveMysql.recreateTables();
    }

    @Test
    void 参与过的人能看_没参与过的人被挡住_管理员都能看() {
        FlowEngine<TestUser> engine = FlowEngine.<TestUser>builder(LiveMysql.easyQuery()).orgDirectory(new TestOrg(EVERYONE))
            .permissionPolicy(ADMIN_SEARCH).build();
        engine.publishDefinition(ADMIN, PROCESS, null);
        var started = engine.start(ALICE, StartCommand.of("memo", Map.of()));
        Long id = started.getInstance().getId();
        Long reviewItem = started.getCreated().get(0).getId();
        engine.cc(ALICE, new CcCommand(id, List.of(ERIN.getId()), "知会"));

        engine.instance(ALICE, id);                      // 发起人
        engine.workItem(CAROL, reviewItem);              // 角色收件箱里的候选
        engine.instance(ERIN, id);                       // 被抄送的
        engine.instance(ADMIN, id);                      // 有搜实例的权限
        assertNotVisible(() -> engine.instance(DAVE, id));
        assertNotVisible(() -> engine.workItem(DAVE, reviewItem));
        assertNotVisible(() -> engine.instance(BOB, id));   // 还没轮到他

        engine.claim(CAROL, new ClaimCommand(reviewItem));
        var reviewed = engine.complete(CAROL, CompleteCommand.of(reviewItem, "ok"));
        engine.instance(BOB, id);                        // 轮到他了
        engine.instance(CAROL, id);                      // 处理过
        engine.complete(BOB, CompleteCommand.of(reviewed.getCreated().get(0).getId(), "ok"));

        assertEquals(1, engine.archive(Duration.ZERO));
        engine.instance(BOB, id);                        // 归档了照样按参与过算
        assertNotVisible(() -> engine.instance(DAVE, id));
    }

    @Test
    void 不配权限时谁都能看() {
        FlowEngine<TestUser> engine = FlowEngine.<TestUser>builder(LiveMysql.easyQuery()).orgDirectory(new TestOrg(EVERYONE)).build();
        engine.publishDefinition(ADMIN, PROCESS, null);
        Long id = engine.start(ALICE, StartCommand.of("memo", Map.of())).getInstance().getId();
        assertEquals(id, engine.instance(DAVE, id).getInstance().getId());
    }

    @Test
    void 自己的规则替换默认的() {
        FlowEngine<TestUser> engine = FlowEngine.<TestUser>builder(LiveMysql.easyQuery()).orgDirectory(new TestOrg(EVERYONE))
            .viewPolicy((actor, viewing) -> actor.getId().equals(viewing.getStartedBy())).build();
        engine.publishDefinition(ADMIN, PROCESS, null);
        Long id = engine.start(ALICE, StartCommand.of("memo", Map.of())).getInstance().getId();
        engine.instance(ALICE, id);
        assertNotVisible(() -> engine.instance(ADMIN, id));
    }

    private static void assertNotVisible(Executable call) {
        FlowException e = assertThrows(FlowException.class, call);
        assertEquals(FlowErrorCode.FORBIDDEN, e.getCode());
        assertEquals("not-visible", e.getParams().get("reason"));
    }
}
