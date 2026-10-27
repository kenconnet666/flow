package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.spi.Choice;
import org.dpdns.zerodep.flow.core.spi.ExtensionInfo;
import org.dpdns.zerodep.flow.core.spi.ExtensionParam;
import org.dpdns.zerodep.flow.core.spi.PeopleResolver;
import org.dpdns.zerodep.flow.core.view.definition.ExtensionCatalog;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 按名字注册的扩展：同名后加的换掉先加的；找不到时报 extension-failed；目录按名字排，没给 info 的显示名同名字 */
class ExtensionsTest {
    private static PeopleResolver resolver(String name, ExtensionInfo info) {
        return new PeopleResolver() {
            @Override public String name() { return name; }

            @Override public List<Long> resolve(Context context) { return List.of(); }

            @Override public ExtensionInfo info() { return info; }
        };
    }

    @Test
    void 同名后加的换掉先加的() {
        PeopleResolver first = resolver("leader", null);
        PeopleResolver second = resolver("leader", ExtensionInfo.of("上级"));
        Extensions extensions = Extensions.builder().resolver(first).resolver(second).build();
        assertSame(second, extensions.resolver("leader"));
        assertTrue(extensions.hasResolver("leader"));
        assertFalse(extensions.hasResolver("nobody"));
    }

    @Test
    void 找不到时报扩展失败() {
        FlowException error = assertThrows(FlowException.class, () -> Extensions.NONE.action("push"));
        assertEquals(FlowErrorCode.EXTENSION_FAILED, error.getCode());
        assertEquals("not-registered", error.getParams().get("reason"));
    }

    @Test
    void 目录按名字排_没给说明的显示名字() {
        Extensions extensions = Extensions.builder()
            .resolver(resolver("zeta", null))
            .resolver(resolver("alpha", ExtensionInfo.of("甲").param(ExtensionParam.of("level", "级", "number").required())))
            .nodeMetaField(ExtensionParam.select("status", "状态", new Choice("a", "甲")))
            .build();
        ExtensionCatalog catalog = extensions.catalog(true);
        assertEquals(List.of("alpha", "zeta"), catalog.getResolvers().stream().map(ExtensionCatalog.Entry::getName).toList());
        assertEquals("甲", catalog.getResolvers().get(0).getLabel());
        assertTrue(catalog.getResolvers().get(0).getParams().get(0).isRequired());
        assertEquals("zeta", catalog.getResolvers().get(1).getLabel());
        assertEquals(List.of(), catalog.getResolvers().get(1).getParams());
        assertEquals("status", catalog.getNodeMeta().get(0).getKey());
        assertTrue(catalog.isFormServerCode());
    }
}
