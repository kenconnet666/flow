package org.dpdns.zerodep.flow.it.listener;

import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.FlowEngineBuilder;
import org.dpdns.zerodep.flow.core.command.CompleteCommand;
import org.dpdns.zerodep.flow.core.command.SkipJobCommand;
import org.dpdns.zerodep.flow.core.command.StartCommand;
import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.core.listener.FlowListener;
import org.dpdns.zerodep.flow.core.listener.FlowNotice;
import org.dpdns.zerodep.flow.core.notify.Notification;
import org.dpdns.zerodep.flow.core.notify.NotificationChannel;
import org.dpdns.zerodep.flow.core.view.instance.IncidentView;
import org.dpdns.zerodep.flow.it.support.LiveMysql;
import org.dpdns.zerodep.flow.it.support.MovableClock;
import org.dpdns.zerodep.flow.it.support.TestOrg;
import org.dpdns.zerodep.flow.it.support.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发件箱（可靠投递）：要投的和流转记录同一个事务记成作业，提交后叫醒轮询；作业里投，失败按作业的规则重试，用完进异常处理，可以放弃；
 * 实例结束时不删还没投的。
 */
@DisplayName("发件箱")
class OutboxTest {
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

    private final MovableClock clock = new MovableClock(Instant.parse("2026-10-27T01:00:00Z"));
    private final AtomicInteger wakeUps = new AtomicInteger();

    /** 可靠的监听：前 failTimes 次抛错，之后收下 */
    private static class Mirror implements FlowListener {
        final List<FlowNotice> received = new ArrayList<>();
        int failTimes;

        @Override
        public void on(FlowNotice notice) {
            if (failTimes > 0) {
                failTimes--;
                throw new IllegalStateException("消息队列连不上");
            }
            received.add(notice);
        }

        @Override public Phase phase() { return Phase.RELIABLE; }

        @Override public String listenerName() { return "mirror"; }
    }

    /** 可靠的渠道：收下每条通知 */
    private static class Inbox implements NotificationChannel {
        final List<Notification> sent = new ArrayList<>();

        @Override public void send(Notification notification) { sent.add(notification); }

        @Override public boolean reliable() { return true; }
    }

    @BeforeEach
    void freshTables() {
        LiveMysql.recreateTables();
    }

    private FlowEngine<TestUser> engine(Mirror mirror, Inbox inbox) {
        FlowEngineBuilder<TestUser> builder = FlowEngine.<TestUser>builder(LiveMysql.easyQuery()).clock(clock)
            .orgDirectory(new TestOrg(List.of(ALICE, BOSS))).listener(mirror).channel(inbox);
        FlowEngine<TestUser> engine = builder.build();
        engine.onOutbox(wakeUps::incrementAndGet);
        engine.publishDefinition(ALICE, PROCESS, null);
        return engine;
    }

    @Test
    void 提交后叫醒轮询_作业里投给监听与渠道() {
        Mirror mirror = new Mirror();
        Inbox inbox = new Inbox();
        FlowEngine<TestUser> engine = engine(mirror, inbox);
        engine.start(ALICE, StartCommand.of("memo", Map.of()));

        assertEquals(1, wakeUps.get());
        assertEquals(0, mirror.received.size(), "提交后只是叫醒，投递在作业里做");
        engine.runDueJobs();
        assertEquals(List.of(EventEntity.INSTANCE_STARTED, EventEntity.NODE_LEFT, EventEntity.NODE_ENTERED, EventEntity.WORK_ITEM_CREATED),
            mirror.received.stream().map(n -> n.getEvent().getType()).toList());
        assertEquals("mail", mirror.received.get(3).getNodeMeta().get("channel"));
        assertEquals(List.of(Notification.ARRIVED), inbox.sent.stream().map(Notification::getKind).toList());
        assertEquals(List.of(BOSS.getId()), inbox.sent.get(0).getRecipients());
        assertEquals("阅", inbox.sent.get(0).getWorkItem().getNodeName());
        assertEquals(0, engine.runDueJobs(), "投完的作业删掉了");
    }

    @Test
    void 实例结束时还没投的不删() {
        Mirror mirror = new Mirror();
        FlowEngine<TestUser> engine = engine(mirror, new Inbox());
        var started = engine.start(ALICE, StartCommand.of("memo", Map.of()));
        engine.complete(BOSS, CompleteCommand.of(started.getCreated().get(0).getId(), "ok"));

        engine.runDueJobs();
        List<String> types = mirror.received.stream().map(n -> n.getEvent().getType()).toList();
        assertEquals(EventEntity.INSTANCE_STARTED, types.get(0));
        assertEquals(EventEntity.INSTANCE_FINISHED, types.get(types.size() - 1));
    }

    @Test
    void 失败按作业的规则重试_用完进异常处理_可以放弃() {
        Mirror mirror = new Mirror();
        mirror.failTimes = 100;
        FlowEngine<TestUser> engine = engine(mirror, new Inbox());
        Long instanceId = engine.start(ALICE, StartCommand.of("memo", Map.of())).getInstance().getId();

        engine.runDueJobs();
        for (int attempt = 1; attempt < 5; attempt++) {
            clock.moveTo(clock.instant().plus(Duration.ofMinutes((long) attempt * attempt)));
            engine.runDueJobs();
        }
        List<IncidentView> incidents = engine.incidents(ALICE, instanceId, 1, 10).getRows();
        assertEquals(1, incidents.size());
        IncidentView failed = incidents.get(0);
        assertEquals("deliver", failed.getKind());
        assertEquals(5, failed.getAttempts());
        assertEquals("消息队列连不上", failed.getError());
        assertTrue(failed.isSkippable());

        engine.skipJob(ALICE, new SkipJobCommand(failed.getJobId(), null, "对方系统下线了"));
        assertEquals(0, engine.incidents(ALICE, instanceId, 1, 10).getTotal());
        assertEquals(0, mirror.received.size());
    }

    @Test
    void 重试时整份再投_接入方按编号去重() {
        Mirror mirror = new Mirror();
        mirror.failTimes = 1;
        FlowEngine<TestUser> engine = engine(mirror, new Inbox());
        engine.start(ALICE, StartCommand.of("memo", Map.of()));

        engine.runDueJobs();
        assertEquals(0, mirror.received.size());
        clock.moveTo(clock.instant().plus(Duration.ofMinutes(1)));
        engine.runDueJobs();
        assertEquals(4, mirror.received.size());
    }

    @Test
    void 可靠的监听与渠道不能重名() {
        Inbox one = new Inbox();
        Inbox other = new Inbox();
        assertThrows(IllegalArgumentException.class, () -> FlowEngine.<TestUser>builder(LiveMysql.easyQuery())
            .orgDirectory(new TestOrg(List.of(ALICE))).channel(one).channel(other).build());
    }
}
