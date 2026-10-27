package org.dpdns.zerodep.flow.it.design;

import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.command.FormPublication;
import org.dpdns.zerodep.flow.core.command.StartCommand;
import org.dpdns.zerodep.flow.core.spi.Choice;
import org.dpdns.zerodep.flow.core.spi.ComputedValue;
import org.dpdns.zerodep.flow.core.spi.ExtensionInfo;
import org.dpdns.zerodep.flow.core.spi.ExtensionParam;
import org.dpdns.zerodep.flow.core.spi.PeopleResolver;
import org.dpdns.zerodep.flow.core.view.definition.ExtensionCatalog;
import org.dpdns.zerodep.flow.it.support.LiveMysql;
import org.dpdns.zerodep.flow.it.support.TestOrg;
import org.dpdns.zerodep.flow.it.support.TestUser;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 给设计器的扩展目录（名字、显示名、参数、结果、节点业务信息的字段），条件里的计算值 fn.名字，关掉在线后端代码。
 */
@DisplayName("扩展目录与计算值")
class ExtensionCatalogTest {
    private static final TestUser ALICE = TestUser.of(1001L, "林小满");
    private static final TestUser BOB = TestUser.of(1002L, "周启航");
    private static final TestUser CAROL = TestUser.of(1003L, "沈知远");
    private static final TestUser ADMIN = TestUser.of(1000L, "管理员");

    /** 金额落在哪一档：阈值在接入方手里，调整时不用重新发布流程 */
    private static final ComputedValue AMOUNT_LEVEL = new ComputedValue() {
        @Override public String name() { return "amountLevel"; }

        @Override
        public Object compute(Context context) {
            Object amount = context.getRead().apply("form.amount");
            return amount instanceof BigDecimal a && a.compareTo(new BigDecimal("10000")) >= 0 ? "high" : "normal";
        }

        @Override
        public ExtensionInfo info() { return ExtensionInfo.of("金额档位").valueType("text"); }
    };

    private static final PeopleResolver LEADER = new PeopleResolver() {
        @Override public String name() { return "leader"; }

        @Override public List<Long> resolve(Context context) { return List.of(CAROL.getId()); }

        @Override
        public ExtensionInfo info() {
            return ExtensionInfo.of("上级").describedAs("发起人往上第 N 级").param(ExtensionParam.of("level", "往上几级", "number").required());
        }
    };

    private static final String MANIFEST = """
        { "amount": { "label": "金额", "type": "decimal", "required": true } }
        """;

    private static final String ROUTED = """
        { "key": "routed", "name": "按档位走", "title": "按档位走", "form": { "key": "amount-form" },
          "nodes": [ { "id": "start", "type": "start", "name": "提交" },
                     { "id": "choose", "type": "exclusive", "name": "看档位" },
                     { "id": "big", "type": "task", "name": "大额审批", "assignees": [{ "resolver": "leader", "args": { "level": 1 } }], "outcomes": [{ "key": "ok", "label": "同意" }] },
                     { "id": "small", "type": "task", "name": "常规审批", "assignees": [{ "user": 1002 }], "outcomes": [{ "key": "ok", "label": "同意" }] },
                     { "id": "end", "type": "end", "name": "结束", "result": "done" } ],
          "edges": [ { "id": "e1", "from": "start", "to": "choose" },
                     { "id": "e2", "from": "choose", "to": "big", "when": { "field": "fn.amountLevel", "op": "eq", "value": "high" } },
                     { "id": "e3", "from": "choose", "to": "small", "default": true },
                     { "id": "e4", "from": "big", "to": "end", "outcome": "ok" }, { "id": "e5", "from": "small", "to": "end", "outcome": "ok" } ] }
        """;

    @BeforeEach
    void setUp() {
        LiveMysql.recreateTables();
    }

    @Test
    void 目录列出注册的扩展与节点业务信息的字段() {
        FlowEngine<TestUser> engine = builder().resolver(LEADER).computed(AMOUNT_LEVEL)
            .nodeMetaField(ExtensionParam.select("bizStatus", "业务状态", new Choice("reviewing", "审核中"), new Choice("done", "已完成")))
            .build();
        ExtensionCatalog catalog = engine.extensions(ADMIN);

        ExtensionCatalog.Entry leader = catalog.getResolvers().get(0);
        assertEquals("leader", leader.getName());
        assertEquals("上级", leader.getLabel());
        assertEquals("level", leader.getParams().get(0).getKey());
        assertTrue(leader.getParams().get(0).isRequired());
        assertEquals("text", catalog.getComputed().get(0).getValueType());
        assertEquals("bizStatus", catalog.getNodeMeta().get(0).getKey());
        assertEquals(2, catalog.getNodeMeta().get(0).getOptions().size());
        assertTrue(catalog.isFormServerCode());
        assertEquals(List.of(), catalog.getActions());
    }

    @Test
    void 条件里的计算值按接入方的规则走() {
        FlowEngine<TestUser> engine = builder().resolver(LEADER).computed(AMOUNT_LEVEL).build();
        engine.publishForm(ADMIN, new FormPublication("amount-form", "金额", null, MANIFEST, null, null, null, null, null, null));
        engine.publishDefinition(ADMIN, ROUTED, null);

        var big = engine.start(ALICE, StartCommand.of("routed", Map.of("amount", new BigDecimal("25000")))).getCreated().get(0);
        assertEquals("big", big.getNode());
        assertEquals(CAROL.getId(), big.getAssigneeId());
        var small = engine.start(ALICE, StartCommand.of("routed", Map.of("amount", new BigDecimal("300")))).getCreated().get(0);
        assertEquals("small", small.getNode());
        assertEquals(BOB.getId(), small.getAssigneeId());
    }

    @Test
    void 没注册的计算值发布时报出来() {
        FlowEngine<TestUser> engine = builder().resolver(LEADER).build();
        engine.publishForm(ADMIN, new FormPublication("amount-form", "金额", null, MANIFEST, null, null, null, null, null, null));
        FlowException error = assertThrows(FlowException.class, () -> engine.publishDefinition(ADMIN, ROUTED, null));
        assertTrue(error.getErrors().stream().anyMatch(e -> "extension-unknown".equals(e.getCode()) && "computed".equals(e.getParams().get("kind"))),
            () -> String.valueOf(error.getErrors()));
    }

    @Test
    void 关掉在线后端代码时带后端代码的表单发布不了() {
        FlowEngine<TestUser> engine = builder().formServerCode(false).build();
        assertFalse(engine.extensions(ADMIN).isFormServerCode());
        FlowException error = assertThrows(FlowException.class, () -> engine.publishForm(ADMIN,
            new FormPublication("amount-form", "金额", null, MANIFEST, null, null, null, "java", "public class Handler {}", null)));
        assertEquals(FlowErrorCode.VALIDATION_FAILED, error.getCode());
        assertEquals("server-code-disabled", error.getErrors().get(0).getCode());
        engine.publishForm(ADMIN, new FormPublication("amount-form", "金额", null, MANIFEST, null, null, null, null, null, null));
    }

    private static org.dpdns.zerodep.flow.core.FlowEngineBuilder<TestUser> builder() {
        return FlowEngine.<TestUser>builder(LiveMysql.easyQuery()).orgDirectory(new TestOrg(List.of(ALICE, BOB, CAROL, ADMIN)));
    }
}
