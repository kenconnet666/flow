package org.dpdns.zerodep.flow.model;

import org.dpdns.zerodep.flow.model.definition.DefinitionSchema;
import org.dpdns.zerodep.flow.model.definition.Definitions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 定义的格式版本：不写是第 1 版；当前版照读；比引擎还新的报 Newer；写得不对的报形状错 */
class DefinitionSchemaTest {
    private static String definition(String schema) {
        return "{ " + schema + " \"key\": \"p\", \"name\": \"p\", \"nodes\": [{ \"id\": \"start\", \"type\": \"start\", \"name\": \"提交\" }], \"edges\": [] }";
    }

    @Test
    void 不写与当前版照读() {
        assertEquals("p", Definitions.parse(definition("")).getKey());
        assertEquals("p", Definitions.parse(definition("\"schema\": " + DefinitionSchema.CURRENT + ",")).getKey());
    }

    @Test
    void 比引擎还新的报出来() {
        DefinitionSchema.Newer newer = assertThrows(DefinitionSchema.Newer.class,
            () -> Definitions.parse(definition("\"schema\": " + (DefinitionSchema.CURRENT + 1) + ",")));
        assertEquals(DefinitionSchema.CURRENT + 1, newer.getVersion());
    }

    @Test
    void 写得不对的是形状错() {
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class, () -> Definitions.parse(definition("\"schema\": \"one\",")));
        assertEquals("$.schema: must be a positive integer", wrong.getMessage());
    }
}
