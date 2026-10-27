package org.dpdns.zerodep.flow.it.design;

import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.command.FormPublication;
import org.dpdns.zerodep.flow.core.view.form.FormUsage;
import org.dpdns.zerodep.flow.it.support.LiveMysql;
import org.dpdns.zerodep.flow.it.support.TestOrg;
import org.dpdns.zerodep.flow.it.support.TestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 用了某张表单的流程带上它定下的表单版本：表单再发布，流程还定在旧版本上，重新发布流程才换（表单编辑器据此提示） */
@DisplayName("表单的使用处")
class FormUsageTest {
    private static final TestUser ADMIN = TestUser.of(1000L, "管理员");
    private static final String MANIFEST = "{\"title\":{\"label\":\"标题\",\"type\":\"text\"}}";
    private static final String PROCESS = """
        { "key": "memo", "name": "备忘", "title": "备忘", "form": { "key": "memo-form" },
          "nodes": [ { "id": "start", "type": "start", "name": "提交" }, { "id": "end", "type": "end", "name": "结束" } ],
          "edges": [ { "id": "e1", "from": "start", "to": "end" } ] }
        """;

    private FlowEngine<TestUser> engine;

    @BeforeEach
    void setUp() {
        LiveMysql.recreateTables();
        engine = FlowEngine.<TestUser>builder(LiveMysql.easyQuery()).orgDirectory(new TestOrg(List.of(ADMIN))).build();
    }

    @Test
    void 表单再发布后流程还定在旧版本_重新发布流程才换() {
        engine.publishForm(ADMIN, form());
        engine.publishDefinition(ADMIN, PROCESS, null);
        engine.publishForm(ADMIN, form());

        FormUsage usage = engine.formUsages(ADMIN, "memo-form").get(0);
        assertEquals("memo", usage.getProcessKey());
        assertEquals(1, usage.getVersion());
        assertEquals(1, usage.getFormVersion());

        engine.publishDefinition(ADMIN, PROCESS, null);
        assertEquals(2, engine.formUsages(ADMIN, "memo-form").get(0).getFormVersion());
    }

    private static FormPublication form() {
        return new FormPublication("memo-form", "备忘单", null, MANIFEST, null, null, null, null, null, null);
    }
}
