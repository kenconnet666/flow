/** 强类型 JSON */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Code, CodeBlock, Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';

export const meta = {
  title: '强类型 JSON',
  description: '金额、长编号、日期在 JSON 里会丢精度或变成字符串。引擎的接口用 json-wire 的 typed JSON，响应怎么编码由接入方配置。',
  order: 4,
};

const wire = `{
  "code": "ok",
  "data": { "id": "184467440737095516", "amount": "16000.00", "startedAt": "2026-10-28T06:02:11Z" },
  "$types": { "data.id": "bigint", "data.amount": "decimal", "data.startedAt": "timestamptz" }
}`;

const advice = `@RestControllerAdvice(basePackages = {"org.dpdns.zerodep.flow.spring", "com.example.app.web"})
class WireResponseAdvice implements ResponseBodyAdvice<Object> {
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) { return true; }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        // 只改 JSON 的响应；文字、错误页、已经编码过的原样
        if (body == null || body instanceof ProblemDetail || body instanceof CharSequence || isEncoded(body)
            || !MediaType.APPLICATION_JSON.isCompatibleWith(contentType)) return body;
        return Wire.encode(body);
    }

    private static boolean isEncoded(Object body) {
        return body instanceof Map<?, ?> map && (map.containsKey(Wire.TYPES) || map.containsKey(Wire.VALUE));
    }
}`;

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="why" title="为什么">
      <Paragraph>
        JavaScript 的数只有双精度：超过 2^53 的编号会被改掉末几位，<Code>0.1 + 0.2</Code> 不等于 <Code>0.3</Code>；日期在 JSON 里只是字符串。审批里金额、单据编号、时限都很常见，所以引擎的接口用 <Code>json-wire</Code> 的 typed JSON：这类值写成文字，根上的 <Code>$types</Code> 记下位置与类型，前端的请求层还原成 bigint、Decimal、日期对象。
      </Paragraph>
      <CodeBlock title="响应的样子" code={wire} lang="json" />
    </Section>
    <Section id="backend" title="后端：由你配置">
      <Paragraph>
        编码响应是 Spring 适配层的事，不在引擎核心里；而且不是每个控制器都要转换，所以<strong>由接入方自己配置</strong>：把示例项目的 <Code>config/wire/WireResponseAdvice.java</Code> 抄进你的项目，<Code>basePackages</Code> 写上引擎的包 <Code>org.dpdns.zerodep.flow.spring</Code> 和你自己要转换的控制器包。
      </Paragraph>
      <CodeBlock code={advice} lang="java" />
      <Paragraph>
        请求体不用处理：引擎自己的接口按 typed JSON 读请求体；你自己的控制器收到的 bigint、小数、日期都是文字，Jackson 照常读成 Long、BigDecimal、LocalDate。自己的值类型（例如金额类）用 <Code>Wire.register</Code> 注册。
      </Paragraph>
    </Section>
    <Section id="frontend" title="前端">
      <Paragraph>
        <Code>{"createHttpClient({ wire: { decimal: Decimal, dates: 'objects' } })"}</Code> 打开还原（见 前端接入）。表单的值在前端也是强类型：金额字段是 Decimal，日期字段是 <Code>CalendarDate</Code>，编号是 bigint。
      </Paragraph>
      <Alert tone="warning">
        漏配时页面不报错，但金额成了浮点、大编号丢精度、日期是字符串。flow-ui 发现响应里的编号是普通数字时，会在浏览器控制台提示一次"/flow 接口的响应不是 typed JSON"——看到它就检查 WireResponseAdvice 的 basePackages 与前端的 wire 选项。
      </Alert>
    </Section>
  </DocPage>
));
