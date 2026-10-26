package org.dpdns.zerodep.flow.it;

import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.command.ClaimCommand;
import org.dpdns.zerodep.flow.core.command.CommandResult;
import org.dpdns.zerodep.flow.core.command.CompleteCommand;
import org.dpdns.zerodep.flow.core.command.FormPublication;
import org.dpdns.zerodep.flow.core.command.InstanceDetail;
import org.dpdns.zerodep.flow.core.command.StartCommand;
import org.dpdns.zerodep.flow.core.command.WithdrawCommand;
import org.dpdns.zerodep.flow.core.data.FormValues;
import org.dpdns.zerodep.flow.core.id.SequentialIds;
import org.dpdns.zerodep.flow.it.support.LiveMysql;
import org.dpdns.zerodep.flow.it.support.TestOrg;
import org.dpdns.zerodep.flow.it.support.TestUser;
import org.dpdns.zerodep.flow.model.error.ErrorDetail;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;
import org.dpdns.zerodep.flow.model.runtime.FlowEvent;
import org.dpdns.zerodep.flow.model.runtime.ProcessInstance;
import org.dpdns.zerodep.flow.model.runtime.WorkItem;
import org.dpdns.zerodep.query.Sql;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 最小链路（HANDOFF 第 6 节的 ③a）：发布表单与流程，发起，角色收件箱认领，处理，排他网关按金额分路，结束；
 * 另有否决、撤回、重复提交、表单校验、发布校验。流程是 src/test/resources/purchase 下的采购申请：
 * 提交 → 财务复核（角色 finance 的收件箱）→ 金额 ≥ 10000 时总经理审批（boss）→ 结束。
 */
@DisplayName("最小链路：发起、认领、处理、撤回")
class MinimalChainTest {
    private static final TestUser ALICE = TestUser.of("alice", "林小满");
    private static final TestUser BOB = TestUser.of("bob", "周启航", "finance");
    private static final TestUser CAROL = TestUser.of("carol", "沈若水");
    private static final TestUser BOSS = TestUser.of("boss", "总经理");

    private FlowEngine<TestUser> engine;

    @BeforeEach
    void freshEngine() {
        LiveMysql.recreateTables();
        engine = FlowEngine.<TestUser>builder(Sql.database(LiveMysql.dataSource()))
            .orgDirectory(new TestOrg(Map.of("boss", "总经理")))
            .idStrategy(new SequentialIds())
            .clock(Clock.fixed(Instant.parse("2026-10-26T08:00:00Z"), ZoneOffset.UTC))
            .build();
        engine.publishForm(ALICE, new FormPublication("purchase-form", "采购申请单", null, resource("purchase/form.json"),
            null, null, null, null, null, "第一版"));
        engine.publishDefinition(ALICE, resource("purchase/process.json"), "第一版");
    }

    private CommandResult startPurchase(String amount) {
        return engine.start(ALICE, StartCommand.of("purchase", Map.of("title", "办公桌", "amount", amount)));
    }

    @Test
    void 金额大的走到总经理审批后结束() {
        CommandResult started = startPurchase("12000.00");
        assertEquals("采购申请：办公桌", started.instance().title());
        assertEquals(List.of("review"), started.instance().current().stream().map(ProcessInstance.CurrentNode::node).toList());
        WorkItem review = single(started.created());
        assertEquals(List.of(WorkItem.POOL, "role", "finance"), List.of(review.assignment(), review.inboxKind(), review.inboxId()));

        // 收件箱：财务的人看得到，别人看不到；摘要列带着标题与金额
        WorkItem inBobsInbox = single(engine.todo(BOB, 1, 10).rows());
        assertEquals("采购申请：办公桌", inBobsInbox.title());
        assertEquals(Map.of("form.title", "办公桌", "form.amount", "12000.00"), inBobsInbox.summary());
        assertEquals(0, engine.todo(CAROL, 1, 10).total());
        assertCode(FlowErrorCode.NOT_CANDIDATE, () -> engine.claim(CAROL, new ClaimCommand(review.id())));

        // 认领、处理：财务意见在复核节点上能改
        engine.claim(BOB, new ClaimCommand(review.id()));
        assertCode(FlowErrorCode.ALREADY_CLAIMED, () -> engine.claim(CAROL, new ClaimCommand(review.id())));
        CommandResult reviewed = engine.complete(BOB, new CompleteCommand(review.id(), "pass", "同意", FormValues.of(Map.of("note", "预算内"))));
        WorkItem approve = single(reviewed.created());
        assertEquals(List.of("approve", WorkItem.DIRECT, "boss", "总经理"),
            List.of(approve.node(), approve.assignment(), approve.assigneeId(), approve.assigneeName()));

        CommandResult approved = engine.complete(BOSS, CompleteCommand.of(approve.id(), "pass"));
        assertEquals(List.of(ProcessInstance.COMPLETED, "approved"), List.of(approved.instance().status(), approved.instance().result()));
        assertEquals(List.of(), approved.instance().current());

        InstanceDetail detail = engine.instance(ALICE, started.instance().id());
        assertEquals("预算内", detail.form().text("note"));
        assertEquals(List.of(WorkItem.DONE, WorkItem.DONE), detail.workItems().stream().map(WorkItem::state).toList());
        List<String> types = detail.timeline().stream().map(FlowEvent::type).toList();
        assertEquals(FlowEvent.INSTANCE_STARTED, types.get(0));
        assertEquals(FlowEvent.INSTANCE_FINISHED, types.get(types.size() - 1));
        assertTrue(types.contains(FlowEvent.WORK_ITEM_CLAIMED), types.toString());
    }

    @Test
    void 金额小的复核通过就结束() {
        WorkItem review = single(startPurchase("500.00").created());
        CommandResult reviewed = engine.complete(BOB, CompleteCommand.of(review.id(), "pass"));
        assertEquals(List.of(), reviewed.created());
        assertEquals(List.of(ProcessInstance.COMPLETED, "approved"), List.of(reviewed.instance().status(), reviewed.instance().result()));
    }

    @Test
    void 否决要写意见_写了就按否决的出边结束() {
        WorkItem review = single(startPurchase("500.00").created());
        FlowException noComment = assertCode(FlowErrorCode.VALIDATION_FAILED, () -> engine.complete(BOB, CompleteCommand.of(review.id(), "reject")));
        assertEquals(List.of(ErrorDetail.field("comment", "required")), noComment.errors());
        CommandResult rejected = engine.complete(BOB, new CompleteCommand(review.id(), "reject", "预算不够", null));
        assertEquals("rejected", rejected.instance().result());
    }

    @Test
    void 同一个待办提交两次时第二次报已处理() {
        WorkItem review = single(startPurchase("500.00").created());
        engine.complete(BOB, CompleteCommand.of(review.id(), "pass"));
        assertCode(FlowErrorCode.INSTANCE_NOT_ACTIVE, () -> engine.complete(BOB, CompleteCommand.of(review.id(), "pass")));

        WorkItem other = single(startPurchase("12000.00").created());
        engine.complete(BOB, CompleteCommand.of(other.id(), "pass"));
        assertCode(FlowErrorCode.WORK_ITEM_NOT_OPEN, () -> engine.complete(BOB, CompleteCommand.of(other.id(), "pass")));
    }

    @Test
    void 发起时按字段清单校验_开始节点上隐藏的字段不收() {
        FlowException missing = assertCode(FlowErrorCode.VALIDATION_FAILED,
            () -> engine.start(ALICE, StartCommand.of("purchase", Map.of("amount", "100.00"))));
        assertEquals(List.of(ErrorDetail.field("title", "required")), missing.errors());

        CommandResult started = engine.start(ALICE, StartCommand.of("purchase", Map.of("title", "椅子", "amount", "100.00", "note", "偷偷写的")));
        assertNull(engine.instance(ALICE, started.instance().id()).form().get("note"));
    }

    @Test
    void 发起人在有人处理之前能撤回_之后不能() {
        CommandResult first = startPurchase("500.00");
        CommandResult withdrawn = engine.withdraw(ALICE, new WithdrawCommand(first.instance().id(), "写错了"));
        assertEquals(ProcessInstance.WITHDRAWN, withdrawn.instance().status());
        assertEquals(List.of(WorkItem.OPEN), withdrawn.closed().stream().map(WorkItem::state).toList());
        assertEquals(0, engine.todo(BOB, 1, 10).total());

        CommandResult second = startPurchase("12000.00");
        engine.complete(BOB, CompleteCommand.of(single(second.created()).id(), "pass"));
        assertCode(FlowErrorCode.FORBIDDEN, () -> engine.withdraw(ALICE, new WithdrawCommand(second.instance().id(), "晚了")));
        assertCode(FlowErrorCode.FORBIDDEN, () -> engine.withdraw(BOB, new WithdrawCommand(second.instance().id(), "不是我发起的")));
    }

    @Test
    void 发布时校验定义_有错误不发布() {
        String broken = resource("purchase/process.json").replace("\"to\": \"route\"", "\"to\": \"nowhere\"");
        FlowException invalid = assertCode(FlowErrorCode.DEFINITION_INVALID, () -> engine.publishDefinition(ALICE, broken, "坏的"));
        assertTrue(invalid.errors().stream().anyMatch(e -> "edge-unknown-node".equals(e.code()) && "reviewed".equals(e.edge())), invalid.errors().toString());
    }

    // ───────── 工具 ─────────

    private static <T> T single(List<T> items) {
        assertEquals(1, items.size(), items.toString());
        return items.get(0);
    }

    /** 断言抛出带这个业务错误码的异常 */
    private static FlowException assertCode(FlowErrorCode code, Executable action) {
        FlowException e = assertThrows(FlowException.class, action);
        assertEquals(code, e.code(), e.getMessage());
        return e;
    }

    private static String resource(String path) {
        try (InputStream in = MinimalChainTest.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalArgumentException("missing test resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
