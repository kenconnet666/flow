package org.dpdns.zerodep.flow.it.form;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.command.FormPublication;
import org.dpdns.zerodep.flow.core.command.StartCommand;
import org.dpdns.zerodep.flow.core.engine.form.FormCompiler;
import org.dpdns.zerodep.flow.core.engine.store.Instances;
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

/** 表单的后端代码（0004 的 1.5）：发布时编译、编不过不能发布；发起页的初始值、提交时的校验与计算与写业务表、给前端调的接口 */
@DisplayName("表单的后端代码")
class FormCodeTest {
    private static final TestUser ALICE = TestUser.of(1001L, "林小满");

    private static final String MANIFEST = """
        { "amount": { "label": "金额", "type": "decimal", "required": true },
          "total": { "label": "合计", "type": "decimal" },
          "note": { "label": "说明", "type": "text" } }
        """;

    /** 后端代码：金额超过 1000 拦下；合计是金额的两倍；每提交一次计数加一，接口能读到计数 */
    private static final String CODE = """
        import org.dpdns.zerodep.flow.core.spi.FormValues;
        import org.dpdns.zerodep.flow.core.spi.*;
        import java.math.BigDecimal;
        import java.util.Map;
        import java.util.concurrent.atomic.AtomicInteger;

        public class MemoForm implements FormHandler {
            private static final AtomicInteger SUBMITTED = new AtomicInteger();

            @Override public FormValues load(FormContext ctx) { return FormValues.of(Map.of("note", "默认说明")); }

            @Override public FieldErrors validate(FormValues values, FormContext ctx) {
                return values.decimal("amount").compareTo(new BigDecimal("1000")) > 0 ? FieldErrors.of("amount", "超出预算") : FieldErrors.none();
            }

            @Override public FormValues compute(FormValues values, FormContext ctx) {
                return FormValues.of(Map.of("total", values.decimal("amount").multiply(new BigDecimal("2"))));
            }

            @Override public void submit(FormValues values, FormContext ctx) { SUBMITTED.incrementAndGet(); }

            @Override public Map<String, FormAction> actions() {
                return Map.of("submitted", (args, ctx) -> SUBMITTED.get(), "echo", (args, ctx) -> args.get("x"));
            }
        }
        """;

    private static final String PROCESS = """
        { "key": "memo", "name": "备忘", "title": "备忘", "form": { "key": "memo-form" },
          "nodes": [ { "id": "start", "type": "start", "name": "提交" }, { "id": "end", "type": "end", "name": "结束" } ],
          "edges": [ { "id": "e1", "from": "start", "to": "end" } ] }
        """;

    private EasyEntityQuery db;
    private FlowEngine<TestUser> engine;

    @BeforeEach
    void setUp() {
        LiveMysql.recreateTables();
        db = LiveMysql.easyQuery();
        engine = FlowEngine.<TestUser>builder(db).orgDirectory(new TestOrg(List.of(ALICE))).build();
    }

    private static FormPublication form(String code) {
        return new FormPublication("memo-form", "备忘单", null, MANIFEST, null, null, null, "java", code, null);
    }

    @Test
    void 试编译报出行列_编不过的不能发布() {
        List<FormCompiler.Problem> problems = engine.compileFormCode(ALICE, "public class Bad implements org.dpdns.zerodep.flow.core.spi.FormHandler {\n  int x = ;\n}");
        assertFalse(problems.isEmpty());
        assertEquals(2, problems.get(0).getLine());
        assertEquals(List.of(), engine.compileFormCode(ALICE, CODE));

        FlowException error = assertThrows(FlowException.class, () -> engine.publishForm(ALICE, form("public class Broken { oops }")));
        assertEquals(FlowErrorCode.VALIDATION_FAILED, error.getCode());
        assertEquals("compile-error", error.getErrors().get(0).getCode());
    }

    @Test
    void 初始值_校验_计算_写业务表_接口() {
        engine.publishForm(ALICE, form(CODE));
        engine.publishDefinition(ALICE, PROCESS, null);

        assertEquals("默认说明", engine.startView(ALICE, "memo").getForm().getValues().get("note"));

        FlowException rejected = assertThrows(FlowException.class,
            () -> engine.start(ALICE, StartCommand.of("memo", Map.of("amount", new BigDecimal("2000")))));
        assertEquals(FlowErrorCode.VALIDATION_FAILED, rejected.getCode());
        assertEquals("amount", rejected.getErrors().get(0).getField());
        assertEquals("超出预算", rejected.getErrors().get(0).getParams().get("message"));

        var started = engine.start(ALICE, StartCommand.of("memo", Map.of("amount", new BigDecimal("100.50"))));
        Long instanceId = started.getInstance().getId();
        assertEquals(0, new BigDecimal("201.00").compareTo(Instances.form(db, instanceId).decimal("total")));

        Long formVersionId = engine.startView(ALICE, "memo").getForm().getVersionId();
        assertEquals(1, engine.formAction(ALICE, formVersionId, instanceId, "submitted", Map.of()));
        assertEquals("你好", engine.formAction(ALICE, formVersionId, null, "echo", Map.of("x", "你好")));
    }
}
