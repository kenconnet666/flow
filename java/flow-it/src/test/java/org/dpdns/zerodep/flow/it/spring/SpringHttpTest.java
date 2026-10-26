package org.dpdns.zerodep.flow.it.spring;

import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.it.support.LiveMysql;
import org.dpdns.zerodep.flow.it.support.TestUser;
import org.dpdns.zerodep.wire.Json;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * flow-spring 的自动配置与 HTTP 接口（0006 的 7.3）：只配了数据源、打开 Flyway 建表与 HTTP 接口，应用只给了组织架构与当前操作人。
 * 走一单采购：发起、财务处理、总经理审批；HTTP 一律 200，成败看响应体的 code（0008）。
 */
@SpringBootTest(classes = SpringTestApp.class, properties = {
    "flow.flyway.enabled=true",
    "flow.http.enabled=true",
    // 应用自己没有 Flyway 迁移：关掉 Spring Boot 自带的那一套，只跑引擎的
    "spring.flyway.enabled=false"})
@AutoConfigureMockMvc
@DisplayName("Spring Boot：自动配置与 HTTP 接口")
class SpringHttpTest {
    @Autowired MockMvc http;
    /** 注入成带自己用户类型的引擎：自动配置的 Bean 不带泛型参数，所以能这样注入 */
    @Autowired FlowEngine<TestUser> engine;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("FLOW_MYSQL_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("FLOW_MYSQL_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("FLOW_MYSQL_PASSWORD"));
    }

    @BeforeEach
    void freshTablesAndProcess() throws Exception {
        LiveMysql.recreateTables();
        Path scenarios = Path.of("..", "..", "spec", "scenarios");
        // 经接口发布：表单文件与定义文件原样作为请求体
        Map<String, Object> form = call(post("/flow/forms/publish"), "1001",
            Files.readString(scenarios.resolve("forms/purchase-form.json"), StandardCharsets.UTF_8));
        assertEquals(Map.of("id", data(form).get("id"), "key", "purchase-form", "version", 1L), data(form));
        Map<String, Object> process = call(post("/flow/definitions/publish").param("note", "第一版"), "1001",
            Files.readString(scenarios.resolve("processes/purchase.json"), StandardCharsets.UTF_8));
        assertEquals(1L, ((Number) data(process).get("version")).longValue());
        assertEquals("flow.definition-invalid", call(post("/flow/definitions/publish"), "1001", "{\"key\":\"x\"}").get("code"));
    }

    @Test
    void 一单采购走完_出错也是200加业务错误码() throws Exception {
        Map<String, Object> started = call(post("/flow/commands/start"), "1001",
            "{\"key\":\"purchase\",\"form\":{\"title\":\"办公桌\",\"amount\":\"12000.00\"}}");
        assertEquals("ok", started.get("code"));
        String review = (String) first(data(started).get("workItems")).get("id");
        String instance = (String) ((Map<?, ?>) data(started).get("instance")).get("id");

        Map<String, Object> todo = call(get("/flow/queries/todo").param("page", "1").param("pageSize", "10"), "1002", null);
        assertEquals(1L, ((Number) data(todo).get("total")).longValue());

        Map<String, Object> reviewed = call(post("/flow/commands/complete"), "1002",
            "{\"workItemId\":\"" + review + "\",\"outcome\":\"pass\",\"form\":{\"note\":\"预算内\"}}");
        String approve = (String) first(data(reviewed).get("workItems")).get("id");

        Map<String, Object> again = call(post("/flow/commands/complete"), "1002", "{\"workItemId\":\"" + review + "\",\"outcome\":\"pass\"}");
        assertEquals("flow.work-item-not-open", again.get("code"));

        Map<String, Object> malformed = call(post("/flow/commands/complete"), "1002", "{not json");
        assertEquals("flow.validation-failed", malformed.get("code"));
        assertEquals(List.of(Map.of("code", "malformed-json", "field", "body")), malformed.get("errors"));

        Map<String, Object> approved = call(post("/flow/commands/complete"), "1004", "{\"workItemId\":\"" + approve + "\",\"outcome\":\"pass\"}");
        assertEquals("completed", ((Map<?, ?>) data(approved).get("instance")).get("status"));

        Map<String, Object> detail = call(get("/flow/queries/instances/" + instance), "1001", null);
        Map<?, ?> form = (Map<?, ?>) data(detail).get("form");
        assertEquals("预算内", ((Map<?, ?>) form.get("values")).get("note"));
    }

    @Test
    void 带同一个IdempotencyKey的命令只做一次_再来返回第一次的响应() throws Exception {
        Map<String, Object> started = call(post("/flow/commands/start"), "1001",
            "{\"key\":\"purchase\",\"form\":{\"title\":\"椅子\",\"amount\":\"100.00\"}}");
        String review = (String) first(data(started).get("workItems")).get("id");
        String body = "{\"workItemId\":\"" + review + "\",\"outcome\":\"pass\"}";

        Map<String, Object> once = call(post("/flow/commands/complete").header("Idempotency-Key", "k-1"), "1002", body);
        assertEquals("ok", once.get("code"));
        // 不带键再做一次就真的再做：金额小，复核完实例已经结束了
        assertEquals("flow.instance-not-active", call(post("/flow/commands/complete"), "1002", body).get("code"));
        // 带同一个键：返回第一次的响应，不再做
        assertEquals(once, call(post("/flow/commands/complete").header("Idempotency-Key", "k-1"), "1002", body));
        // 同一个键用在别的命令上
        Map<String, Object> reused = call(post("/flow/commands/claim").header("Idempotency-Key", "k-1"), "1002", body);
        assertEquals("flow.validation-failed", reused.get("code"));
    }

    /** 发一个请求：断言 HTTP 是 200，返回响应体 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> call(MockHttpServletRequestBuilder request, String user, String body) throws Exception {
        request.header("X-User", user);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
        var response = http.perform(request).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        return (Map<String, Object>) Json.read(response.getContentAsString(StandardCharsets.UTF_8));
    }

    private static Map<?, ?> data(Map<String, Object> response) { return (Map<?, ?>) response.get("data"); }

    private static Map<?, ?> first(Object list) { return (Map<?, ?>) ((List<?>) list).get(0); }
}
