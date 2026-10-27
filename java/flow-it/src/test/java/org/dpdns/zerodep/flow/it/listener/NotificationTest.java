package org.dpdns.zerodep.flow.it.listener;

import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.command.CcCommand;
import org.dpdns.zerodep.flow.core.command.ClaimCommand;
import org.dpdns.zerodep.flow.core.command.CompleteCommand;
import org.dpdns.zerodep.flow.core.command.StartCommand;
import org.dpdns.zerodep.flow.core.notify.Notification;
import org.dpdns.zerodep.flow.it.support.LiveMysql;
import org.dpdns.zerodep.flow.it.support.TestOrg;
import org.dpdns.zerodep.flow.it.support.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 通知（0005 第 8 节）：默认收件人、定义里换模板与加收件人、关掉某种，事务提交后交给渠道 */
@DisplayName("通知")
class NotificationTest {
    private static final TestUser ALICE = TestUser.of(1001L, "林小满");
    private static final TestUser CAROL = TestUser.of(1003L, "沈若水", 1L);
    private static final TestUser DAVE = TestUser.of(1005L, "陆远", 1L);
    private static final TestUser BOSS = TestUser.of(1004L, "总经理");

    private static final String PROCESS = """
        { "key": "memo", "name": "备忘", "title": "备忘",
          "settings": { "notify": { "cc": false } },
          "nodes": [
            { "id": "start", "type": "start", "name": "提交" },
            { "id": "review", "type": "task", "name": "初审", "assignees": [{ "role": 1 }],
              "notify": { "arrived": { "template": "review-arrived", "to": [{ "user": 1004 }] } },
              "outcomes": [{ "key": "ok", "label": "同意" }] },
            { "id": "approve", "type": "task", "name": "审批", "assignees": [{ "user": 1004 }],
              "outcomes": [{ "key": "ok", "label": "同意" }, { "key": "back", "label": "退回", "kind": "return" }] },
            { "id": "end", "type": "end", "name": "结束", "result": "done" } ],
          "edges": [
            { "id": "e1", "from": "start", "to": "review" },
            { "id": "e2", "from": "review", "to": "approve", "outcome": "ok" },
            { "id": "e3", "from": "approve", "to": "end", "outcome": "ok" },
            { "id": "e4", "from": "approve", "to": "review", "outcome": "back", "kind": "return" } ] }
        """;

    private final List<Notification> sent = new ArrayList<>();
    private FlowEngine<TestUser> engine;

    @BeforeEach
    void setUp() {
        LiveMysql.recreateTables();
        engine = FlowEngine.<TestUser>builder(LiveMysql.easyQuery())
            .orgDirectory(new TestOrg(List.of(ALICE, CAROL, DAVE, BOSS)))
            .channel(sent::add)
            .build();
        engine.publishDefinition(ALICE, PROCESS, null);
    }

    @Test
    void 到达_被退回_结束各发给该发的人_定义里改过的照改() {
        var started = engine.start(ALICE, StartCommand.of("memo", Map.of()));
        Notification arrived = only();
        assertEquals(Notification.ARRIVED, arrived.getKind());
        assertEquals("review-arrived", arrived.getTemplate());
        assertEquals(List.of(1003L, 1005L, 1004L), arrived.getRecipients(), "收件箱的候选人（角色展开），加上 to 写的人");
        assertEquals("初审", arrived.getWorkItem().getNodeName());

        Long review = started.getCreated().get(0).getId();
        engine.claim(CAROL, new ClaimCommand(review));
        var reviewed = engine.complete(CAROL, CompleteCommand.of(review, "ok"));
        Notification toBoss = only();
        assertEquals(List.of(1004L), toBoss.getRecipients());
        assertNull(toBoss.getTemplate(), "审批节点没改，用渠道的默认模板");

        engine.cc(BOSS, new CcCommand(started.getInstance().getId(), List.of(1001L), "看一下"));
        assertEquals(List.of(), sent, "流程级关掉了抄送通知");

        var returned = engine.complete(BOSS, CompleteCommand.of(reviewed.getCreated().get(0).getId(), "back"));
        Notification back = only();
        assertEquals(Notification.RETURNED, back.getKind());
        assertEquals(List.of(1003L), back.getRecipients(), "退回到原来的处理人");

        var again = engine.complete(CAROL, CompleteCommand.of(returned.getCreated().get(0).getId(), "ok"));
        sent.clear();
        engine.complete(BOSS, CompleteCommand.of(again.getCreated().get(0).getId(), "ok"));
        Notification finished = only();
        assertEquals(Notification.FINISHED, finished.getKind());
        assertEquals(List.of(1001L), finished.getRecipients());
        assertEquals("done", finished.getEvent().getOutcome());
    }

    /** 这一步只发了一条，取出来并清空 */
    private Notification only() {
        assertEquals(1, sent.size(), () -> "应只发一条：" + sent.stream().map(Notification::getKind).toList());
        Notification one = sent.get(0);
        sent.clear();
        return one;
    }
}
