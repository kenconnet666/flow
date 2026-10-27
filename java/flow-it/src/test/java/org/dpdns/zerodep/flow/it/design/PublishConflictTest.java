package org.dpdns.zerodep.flow.it.design;

import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.command.FormPublication;
import org.dpdns.zerodep.flow.core.command.SaveDesignDraftCommand;
import org.dpdns.zerodep.flow.it.support.LiveMysql;
import org.dpdns.zerodep.flow.it.support.TestOrg;
import org.dpdns.zerodep.flow.it.support.TestUser;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 发布、存草稿时核对编辑时看到的最新版本：新建撞了别人的标识、打开后别人又发布过，都不覆盖 */
@DisplayName("发布不覆盖别人的")
class PublishConflictTest {
    private static final TestUser ALICE = TestUser.of(1001L, "林小满");
    private static final TestUser BOB = TestUser.of(1002L, "周启航");
    private static final String PROCESS = """
        { "key": "memo", "name": "备忘", "title": "备忘",
          "nodes": [ { "id": "start", "type": "start", "name": "提交" }, { "id": "end", "type": "end", "name": "结束" } ],
          "edges": [ { "id": "e1", "from": "start", "to": "end" } ] }
        """;
    private static final String MANIFEST = "{\"title\":{\"label\":\"标题\",\"type\":\"text\"}}";

    private FlowEngine<TestUser> engine;

    @BeforeEach
    void setUp() {
        LiveMysql.recreateTables();
        engine = FlowEngine.<TestUser>builder(LiveMysql.easyQuery()).orgDirectory(new TestOrg(List.of(ALICE, BOB))).build();
    }

    @Test
    void 新建的流程撞了已有的标识_不发布并说谁发布了第几版() {
        engine.publishDefinition(BOB, PROCESS, null, 0);
        FlowException taken = assertThrows(FlowException.class, () -> engine.publishDefinition(ALICE, PROCESS, null, 0));
        assertEquals(FlowErrorCode.CONCURRENT_MODIFICATION, taken.getCode());
        assertEquals("key-taken", taken.getParams().get("reason"));
        assertEquals("1", taken.getParams().get("latest"));
        assertEquals("周启航", taken.getParams().get("publishedBy"));
    }

    @Test
    void 打开后别人又发布过_带旧的最新版本发布报冲突_带对了照常发布() {
        engine.publishDefinition(ALICE, PROCESS, null, 0);
        engine.publishDefinition(BOB, PROCESS, null, 1);
        FlowException stale = assertThrows(FlowException.class, () -> engine.publishDefinition(ALICE, PROCESS, null, 1));
        assertEquals("newer-version", stale.getParams().get("reason"));
        assertEquals("2", stale.getParams().get("latest"));
        assertEquals(3, engine.publishDefinition(ALICE, PROCESS, null, 2).getVersion());
        // 不给时不核对（脚本、导入）
        assertEquals(4, engine.publishDefinition(ALICE, PROCESS, null).getVersion());
    }

    @Test
    void 新建时的草稿不存到已发布的流程名下_基于某个版本的照常存() {
        engine.publishDefinition(BOB, PROCESS, null, 0);
        FlowException taken = assertThrows(FlowException.class,
            () -> engine.saveDesignDraft(ALICE, new SaveDesignDraftCommand("process", "memo", "备忘", PROCESS, null, null)));
        assertEquals("key-taken", taken.getParams().get("reason"));
        assertNull(engine.designDraft(ALICE, "process", "memo"));
        engine.saveDesignDraft(ALICE, new SaveDesignDraftCommand("process", "memo", "备忘", PROCESS, 1, null));
        assertEquals(1, engine.designDraft(ALICE, "process", "memo").getBaseVersion());
    }

    @Test
    void 表单一样核对() {
        engine.publishForm(BOB, form(0));
        FlowException taken = assertThrows(FlowException.class, () -> engine.publishForm(ALICE, form(0)));
        assertEquals("key-taken", taken.getParams().get("reason"));
        assertEquals(2, engine.publishForm(ALICE, form(1)).getVersion());
        assertEquals("newer-version", assertThrows(FlowException.class, () -> engine.publishForm(BOB, form(1))).getParams().get("reason"));
    }

    private static FormPublication form(int expectedLatest) {
        FormPublication form = new FormPublication("memo-form", "备忘单", null, MANIFEST, null, null, null, null, null, null);
        form.setExpectedLatest(expectedLatest);
        return form;
    }
}
