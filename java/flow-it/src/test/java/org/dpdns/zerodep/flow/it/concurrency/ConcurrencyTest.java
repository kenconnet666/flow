package org.dpdns.zerodep.flow.it.concurrency;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.basic.jdbc.tx.Transaction;
import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.command.ClaimCommand;
import org.dpdns.zerodep.flow.core.command.CommandResult;
import org.dpdns.zerodep.flow.core.command.CompleteCommand;
import org.dpdns.zerodep.flow.core.command.StartCommand;
import org.dpdns.zerodep.flow.core.command.WithdrawCommand;
import org.dpdns.zerodep.flow.it.support.LiveMysql;
import org.dpdns.zerodep.flow.it.support.TestOrg;
import org.dpdns.zerodep.flow.it.support.TestUser;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 并发（0007 第 5 节的锁实例行、带条件的更新）：几个线程同时动同一个待办或同一个实例，只有一个成功，其余的报业务错误码
 * （不是死锁、不是数据库的原始异常），事后的状态是一致的。这要求事务是读已提交（{@code FlowEasyQuery.transaction}）。
 */
@DisplayName("并发")
class ConcurrencyTest {
    private static final int THREADS = 8;
    private static final TestUser ALICE = TestUser.of(1001L, "林小满");
    private static final TestUser BOB = TestUser.of(1002L, "周启航");
    private static final TestUser CAROL = TestUser.of(1003L, "沈知远");
    private static final List<TestUser> CLERKS = new ArrayList<>();

    static {
        for (long i = 0; i < THREADS; i++) CLERKS.add(TestUser.of(2000L + i, "财务" + i, 1L));
    }

    private static final String INBOX = """
        { "key": "inbox", "name": "收件箱", "title": "收件箱",
          "nodes": [ { "id": "start", "type": "start", "name": "提交" },
                     { "id": "check", "type": "task", "name": "财务审核", "assignees": [{ "role": 1 }], "outcomes": [{ "key": "ok", "label": "同意" }] },
                     { "id": "end", "type": "end", "name": "结束", "result": "done" } ],
          "edges": [ { "id": "e1", "from": "start", "to": "check" }, { "id": "e2", "from": "check", "to": "end", "outcome": "ok" } ] }
        """;
    private static final String SIGN = """
        { "key": "sign", "name": "会签", "title": "会签",
          "nodes": [ { "id": "start", "type": "start", "name": "提交" },
                     { "id": "sign", "type": "task", "name": "会签", "assignees": [{ "initiator": true }],
                       "multi": { "participants": [{ "user": 1001 }, { "user": 1002 }, { "user": 1003 }], "complete": "all" },
                       "outcomes": [{ "key": "agree", "label": "同意" }] },
                     { "id": "end", "type": "end", "name": "通过", "result": "approved" } ],
          "edges": [ { "id": "s", "from": "start", "to": "sign" }, { "id": "yes", "from": "sign", "to": "end", "outcome": "agree" } ] }
        """;

    private EasyEntityQuery db;
    private FlowEngine<TestUser> engine;
    private ExecutorService pool;

    @BeforeEach
    void setUp() {
        LiveMysql.recreateTables();
        List<TestUser> everyone = new ArrayList<>(CLERKS);
        everyone.addAll(List.of(ALICE, BOB, CAROL));
        db = LiveMysql.easyQuery();
        engine = FlowEngine.<TestUser>builder(db).orgDirectory(new TestOrg(everyone)).build();
        engine.publishDefinition(ALICE, INBOX, null);
        engine.publishDefinition(ALICE, SIGN, null);
        pool = Executors.newFixedThreadPool(THREADS);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        pool.shutdownNow();
        pool.awaitTermination(10, TimeUnit.SECONDS);
    }

    @Test
    void 几个人同时认领收件箱里的同一个待办_只有一个人认领到() throws Exception {
        Long itemId = engine.start(ALICE, StartCommand.of("inbox", Map.of())).getCreated().get(0).getId();

        List<Callable<CommandResult>> claims = CLERKS.stream().<Callable<CommandResult>>map(c -> () -> engine.claim(c, new ClaimCommand(itemId))).toList();
        Outcomes outcomes = race(claims, Set.of(FlowErrorCode.ALREADY_CLAIMED, FlowErrorCode.WORK_ITEM_NOT_OPEN));

        assertEquals(1, outcomes.succeeded.size());
        Long assignee = engine.workItem(ALICE, itemId).getWorkItem().getAssigneeId();
        assertTrue(CLERKS.stream().anyMatch(c -> c.getId().equals(assignee)), "待办落到了某一个财务手上");
    }

    @Test
    void 同一个人连点几次处理_只处理一次() throws Exception {
        var started = engine.start(ALICE, StartCommand.of("inbox", Map.of()));
        Long itemId = started.getCreated().get(0).getId();
        TestUser clerk = CLERKS.get(0);
        engine.claim(clerk, new ClaimCommand(itemId));

        List<Callable<CommandResult>> completes = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) completes.add(() -> engine.complete(clerk, CompleteCommand.of(itemId, "ok")));
        race(completes, Set.of(FlowErrorCode.WORK_ITEM_NOT_OPEN, FlowErrorCode.INSTANCE_NOT_ACTIVE));

        var detail = engine.instance(ALICE, started.getInstance().getId());
        assertEquals("completed", detail.getInstance().getStatus());
        assertEquals(1, detail.getTimeline().stream().filter(e -> e.getType().equals("instance.finished")).count(), "只结束一次");
    }

    @RepeatedTest(5)
    void 会签的几个人同时同意_实例只往下走一次() throws Exception {
        var started = engine.start(ALICE, StartCommand.of("sign", Map.of()));
        List<Callable<CommandResult>> agrees = new ArrayList<>();
        for (var item : started.getCreated()) {
            TestUser signer = List.of(ALICE, BOB, CAROL).stream().filter(u -> u.getId().equals(item.getAssigneeId())).findFirst().orElseThrow();
            agrees.add(() -> engine.complete(signer, CompleteCommand.of(item.getId(), "agree")));
        }
        Outcomes outcomes = race(agrees, Set.of());

        assertEquals(3, outcomes.succeeded.size(), "三个人各办各的，都成功（实例行上排队）");
        var detail = engine.instance(ALICE, started.getInstance().getId());
        assertEquals("completed", detail.getInstance().getStatus());
        assertEquals("approved", detail.getInstance().getResult());
        assertEquals(1, detail.getTimeline().stream().filter(e -> e.getType().equals("instance.finished")).count(), "只结束一次");
    }

    @RepeatedTest(5)
    void 发起人撤回与审核人处理同时到_只有一个生效() throws Exception {
        var started = engine.start(ALICE, StartCommand.of("inbox", Map.of()));
        Long instanceId = started.getInstance().getId();
        Long itemId = started.getCreated().get(0).getId();
        TestUser clerk = CLERKS.get(0);
        engine.claim(clerk, new ClaimCommand(itemId));

        List<Callable<CommandResult>> both = List.of(
            () -> engine.withdraw(ALICE, new WithdrawCommand(instanceId, "不要了")),
            () -> engine.complete(clerk, CompleteCommand.of(itemId, "ok")));
        Outcomes outcomes = race(both, Set.of(FlowErrorCode.FORBIDDEN, FlowErrorCode.WORK_ITEM_NOT_OPEN, FlowErrorCode.INSTANCE_NOT_ACTIVE));

        assertEquals(1, outcomes.succeeded.size());
        String status = engine.instance(ALICE, instanceId).getInstance().getStatus();
        assertTrue(status.equals("completed") || status.equals("withdrawn"), status);
        String expected = outcomes.succeeded.get(0).getInstance().getStatus();
        assertEquals(expected, status, "最后的状态就是成功的那一个留下的");
    }

    @Test
    void 在外面的可重复读事务里调命令_直接报错而不是悄悄算错() {
        try (Transaction tx = db.beginTransaction(Connection.TRANSACTION_REPEATABLE_READ)) {
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> engine.start(ALICE, StartCommand.of("inbox", Map.of())));
            assertTrue(e.getMessage().contains("READ COMMITTED"), e.getMessage());
            tx.rollback();
        }
        try (Transaction tx = db.beginTransaction(Connection.TRANSACTION_READ_COMMITTED)) {
            engine.start(ALICE, StartCommand.of("inbox", Map.of()));
            tx.commit();
        }
        assertEquals(1, engine.started(ALICE, 1, 20).getTotal(), "读已提交的外面事务里照常加入");
    }

    /** 各个调用排在同一个起跑线上一起放出去；失败的只能是 allowed 里的业务错误码 */
    private Outcomes race(List<Callable<CommandResult>> calls, Set<FlowErrorCode> allowed) throws Exception {
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<CommandResult>> futures = new ArrayList<>();
        for (Callable<CommandResult> call : calls) {
            futures.add(pool.submit(() -> {
                gate.await();
                return call.call();
            }));
        }
        gate.countDown();
        Outcomes outcomes = new Outcomes();
        for (Future<CommandResult> future : futures) {
            try {
                outcomes.succeeded.add(future.get(30, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                if (!(e.getCause() instanceof FlowException flow) || !allowed.contains(flow.getCode())) {
                    fail("expected one of " + allowed + " but got " + e.getCause(), e.getCause());
                }
            }
        }
        return outcomes;
    }

    private static class Outcomes {
        final List<CommandResult> succeeded = new ArrayList<>();
    }
}
