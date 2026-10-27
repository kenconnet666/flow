package org.dpdns.zerodep.flow.it.inbox;

import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.command.CompleteCommand;
import org.dpdns.zerodep.flow.core.command.StartCommand;
import org.dpdns.zerodep.flow.core.view.instance.FlowEventVO;
import org.dpdns.zerodep.flow.core.view.workitem.WorkItemVO;
import org.dpdns.zerodep.flow.it.support.LiveMysql;
import org.dpdns.zerodep.flow.it.support.TestOrg;
import org.dpdns.zerodep.flow.it.support.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 休假代理（OrgDirectory.standIn）：待办要落到不在岗的人时换成代理人，记下代谁处理；被代理的人照样能看实例。
 */
@DisplayName("休假代理")
class StandInTest {
    private static final TestUser ALICE = TestUser.of(1001L, "林小满");
    private static final TestUser BOB = TestUser.of(1002L, "周启航");
    private static final TestUser CAROL = TestUser.of(1003L, "沈知远");
    private static final TestUser ERIN = TestUser.of(1005L, "陆青禾");
    private static final TestUser ADMIN = TestUser.of(1000L, "管理员");
    private static final List<TestUser> EVERYONE = List.of(ALICE, BOB, CAROL, ERIN, ADMIN);

    private static final String MEMO = """
        { "key": "memo", "name": "备忘", "title": "备忘",
          "nodes": [ { "id": "start", "type": "start", "name": "提交" },
                     { "id": "approve", "type": "task", "name": "审批", "assignees": [{ "user": 1002 }], "outcomes": [{ "key": "ok", "label": "同意" }] },
                     { "id": "end", "type": "end", "name": "结束", "result": "done" } ],
          "edges": [ { "id": "e1", "from": "start", "to": "approve" }, { "id": "e2", "from": "approve", "to": "end", "outcome": "ok" } ] }
        """;

    private static final String JOINT = """
        { "key": "joint", "name": "会签", "title": "会签",
          "nodes": [ { "id": "start", "type": "start", "name": "提交" },
                     { "id": "sign", "type": "task", "name": "会签", "assignees": [{ "initiator": true }], "outcomes": [{ "key": "ok", "label": "同意" }],
                       "multi": { "participants": [{ "user": 1002 }, { "user": 1003 }], "complete": "all" } },
                     { "id": "end", "type": "end", "name": "结束", "result": "done" } ],
          "edges": [ { "id": "e1", "from": "start", "to": "sign" }, { "id": "e2", "from": "sign", "to": "end", "outcome": "ok" } ] }
        """;

    @BeforeEach
    void setUp() {
        LiveMysql.recreateTables();
    }

    @Test
    void 落到不在岗的人时换成代理人_记下代谁处理_被代理的人能看() {
        FlowEngine<TestUser> engine = engineWith(Map.of(BOB.getId(), ERIN.getId()));
        engine.publishDefinition(ADMIN, MEMO, null);
        var started = engine.start(ALICE, StartCommand.of("memo", Map.of()));
        WorkItemVO item = started.getCreated().get(0);

        assertEquals(ERIN.getId(), item.getAssigneeId());
        assertEquals(BOB.getId(), item.getOnBehalfOfId());
        assertEquals("周启航", item.getOnBehalfOfName());
        Long id = started.getInstance().getId();
        FlowEventVO arrived = engine.instance(ADMIN, id).getTimeline().stream().filter(e -> e.getType().equals("workItem.created")).findFirst().orElseThrow();
        // 流转记录的附加数据读回来是 JSON：数字的 Java 类型不定，按文字比
        assertEquals("[{userId=1002, userName=周启航, standInId=1005, standInName=陆青禾}]", String.valueOf(arrived.getData().get("standIns")));

        engine.instance(BOB, id);   // 被代理的人算参与过（ViewPolicy 默认规则），收紧权限时也能看
        engine.complete(ERIN, CompleteCommand.of(item.getId(), "ok"));
        assertEquals("completed", engine.instance(ADMIN, id).getInstance().getStatus());
    }

    @Test
    void 代理人也不在岗时接着找_绕回来就停() {
        FlowEngine<TestUser> chain = engineWith(Map.of(BOB.getId(), ERIN.getId(), ERIN.getId(), CAROL.getId()));
        chain.publishDefinition(ADMIN, MEMO, null);
        WorkItemVO item = chain.start(ALICE, StartCommand.of("memo", Map.of())).getCreated().get(0);
        assertEquals(CAROL.getId(), item.getAssigneeId());
        assertEquals(BOB.getId(), item.getOnBehalfOfId());

        LiveMysql.recreateTables();
        FlowEngine<TestUser> loop = engineWith(Map.of(BOB.getId(), ERIN.getId(), ERIN.getId(), BOB.getId()));
        loop.publishDefinition(ADMIN, MEMO, null);
        assertEquals(ERIN.getId(), loop.start(ALICE, StartCommand.of("memo", Map.of())).getCreated().get(0).getAssigneeId());
    }

    @Test
    void 会签的参与人不在岗时由代理人投票() {
        FlowEngine<TestUser> engine = engineWith(Map.of(BOB.getId(), ERIN.getId()));
        engine.publishDefinition(ADMIN, JOINT, null);
        var started = engine.start(ALICE, StartCommand.of("joint", Map.of()));
        List<WorkItemVO> items = started.getCreated();

        assertEquals(List.of(ERIN.getId(), CAROL.getId()), items.stream().map(WorkItemVO::getAssigneeId).toList());
        assertEquals(BOB.getId(), items.get(0).getOnBehalfOfId());
        assertNull(items.get(1).getOnBehalfOfId());
        engine.complete(ERIN, CompleteCommand.of(items.get(0).getId(), "ok"));
        engine.complete(CAROL, CompleteCommand.of(items.get(1).getId(), "ok"));
        assertEquals("completed", engine.instance(ADMIN, started.getInstance().getId()).getInstance().getStatus());
    }

    /** 组织架构：absent 里的人不在岗，交给对应的代理人 */
    private static FlowEngine<TestUser> engineWith(Map<Long, Long> absent) {
        TestOrg org = new TestOrg(EVERYONE) {
            @Override
            public Optional<Long> standIn(Long userId, String processKey, Instant at) {
                return Optional.ofNullable(absent.get(userId));
            }
        };
        return FlowEngine.<TestUser>builder(LiveMysql.easyQuery()).orgDirectory(org).build();
    }
}
