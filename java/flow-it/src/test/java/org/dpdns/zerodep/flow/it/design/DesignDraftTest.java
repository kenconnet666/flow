package org.dpdns.zerodep.flow.it.design;

import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.command.SaveDesignDraftCommand;
import org.dpdns.zerodep.flow.core.view.definition.DesignDraftVO;
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

/** 设计中的草稿（0004 的 5.2）：版本列防两个人互相覆盖，发布成功后删掉 */
@DisplayName("设计中的草稿")
class DesignDraftTest {
    private static final TestUser ALICE = TestUser.of(1001L, "林小满");
    private static final TestUser BOB = TestUser.of(1002L, "周启航");
    private static final String PROCESS = """
        { "key": "memo", "name": "备忘", "title": "备忘",
          "nodes": [ { "id": "start", "type": "start", "name": "提交" }, { "id": "end", "type": "end", "name": "结束" } ],
          "edges": [ { "id": "e1", "from": "start", "to": "end" } ] }
        """;

    private FlowEngine<TestUser> engine;

    @BeforeEach
    void setUp() {
        LiveMysql.recreateTables();
        engine = FlowEngine.<TestUser>builder(LiveMysql.easyQuery()).orgDirectory(new TestOrg(List.of(ALICE, BOB))).build();
    }

    @Test
    void 带着读到的版本保存_别人先存过时报冲突不覆盖() {
        DesignDraftVO first = engine.saveDesignDraft(ALICE, new SaveDesignDraftCommand("process", "memo", "备忘", "{\"v\":1}", null, null));
        assertEquals(0, first.getRev());
        assertEquals("林小满", first.getUpdatedByName());

        DesignDraftVO bobs = engine.saveDesignDraft(BOB, new SaveDesignDraftCommand("process", "memo", "备忘", "{\"v\":2}", null, first.getRev()));
        assertEquals(1, bobs.getRev());

        // 林小满还拿着旧版本：不能覆盖，冲突里带上最后修改人
        FlowException conflict = assertThrows(FlowException.class,
            () -> engine.saveDesignDraft(ALICE, new SaveDesignDraftCommand("process", "memo", "备忘", "{\"v\":3}", null, first.getRev())));
        assertEquals(FlowErrorCode.CONCURRENT_MODIFICATION, conflict.getCode());
        assertEquals("周启航", conflict.getParams().get("updatedBy"));
        assertEquals("{\"v\":2}", engine.designDraft(ALICE, "process", "memo").getContent());

        // 列表不带内容
        List<DesignDraftVO> list = engine.designDrafts(ALICE, "process");
        assertEquals(1, list.size());
        assertNull(list.get(0).getContent());
    }

    @Test
    void 发布成功后草稿删掉_再带旧版本保存报冲突() {
        DesignDraftVO draft = engine.saveDesignDraft(ALICE, new SaveDesignDraftCommand("process", "memo", "备忘", PROCESS, null, null));
        engine.publishDefinition(ALICE, PROCESS, null);
        assertNull(engine.designDraft(ALICE, "process", "memo"));
        assertThrows(FlowException.class,
            () -> engine.saveDesignDraft(ALICE, new SaveDesignDraftCommand("process", "memo", "备忘", PROCESS, 1, draft.getRev())));

        engine.saveDesignDraft(ALICE, new SaveDesignDraftCommand("process", "memo", "备忘", PROCESS, 1, null));
        engine.discardDesignDraft(ALICE, "process", "memo");
        assertNull(engine.designDraft(ALICE, "process", "memo"));
    }
}
