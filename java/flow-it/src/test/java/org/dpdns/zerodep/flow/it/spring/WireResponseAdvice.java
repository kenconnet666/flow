package org.dpdns.zerodep.flow.it.spring;

import org.dpdns.zerodep.wire.Wire;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Map;

/**
 * 接口的 JSON 响应一律写成 typed JSON（json-wire）：Long、BigDecimal、日期等 JavaScript 无法原样承载的值写成文字，
 * 根对象上的 {@code $types} 记下它们的位置，前端的请求层（vue-tsx-pro 的 {@code createHttpClient}）据此还原。
 * 控制器（含引擎自带的 {@code /flow} 接口）直接返回带 Long、BigDecimal、LocalDate 的对象即可。
 *
 * <p>这是接入方自己的配置：处理哪些包、要不要注册自己的值类型（{@code Wire.register}）都在这里改。
 * 测试应用扮演接入方，照示例项目的 {@code config/wire} 写；已经是 typed JSON 的结果原样输出。
 */
@RestControllerAdvice(basePackages = "org.dpdns.zerodep.flow.spring")
class WireResponseAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        // 只改 JSON 的响应（Jackson 2、3 都是）；文字、错误页这些原样
        if (body == null || body instanceof ProblemDetail || body instanceof CharSequence || isEncoded(body)
            || !MediaType.APPLICATION_JSON.isCompatibleWith(selectedContentType)) {
            return body;
        }
        return Wire.encode(body);
    }

    private static boolean isEncoded(Object body) {
        return body instanceof Map<?, ?> map && (map.containsKey(Wire.TYPES) || map.containsKey(Wire.VALUE));
    }
}
