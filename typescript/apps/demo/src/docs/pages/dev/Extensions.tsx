/** 扩展点 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Code, CodeBlock, Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Shot } from '../../site/Shot';
import { Tbl } from '../../site/Tbl';

export const meta = {
  title: '扩展点',
  description: '流程里要你自己的逻辑时（发起人的上级、预算够不够、推送 ERP、金额档位、外部核验），实现一个接口、声明成 Bean，按名字在流程里引用；给出 info() 设计器就认得它。',
  order: 6,
};

const resolver = `@Component
public class LeaderResolver implements PeopleResolver {
    @Override
    public String name() { return "app.leader"; }

    @Override
    public ExtensionInfo info() {
        return ExtensionInfo.of("发起人的上级").describedAs("发起人往上第几级的负责人")
            .param(ExtensionParam.of("level", "往上几级", "number").required().describedAs("1 是直接上级"));
    }

    @Override
    public List<Long> resolve(Context context) {
        int level = ((Number) context.getArgs().get("level")).intValue();
        return List.of(org.leaderOf(context.getInstance().getStartedBy(), level));   // 按你的汇报关系查
    }
}`;

const guard = `@Component
public class AmountCapGuard implements OutcomeGuard {
    private static final BigDecimal CAP = new BigDecimal("1000000");

    @Override
    public String name() { return "app.amount-cap"; }

    @Override
    public ExtensionInfo info() { return ExtensionInfo.of("金额不超过一百万").describedAs("表单的金额超过一百万时不能提交"); }

    /** 返回 null 是通过；返回文字是不通过的原因，整个处理不做 */
    @Override
    public String check(Context context) {
        return context.getForm().get("amount") instanceof BigDecimal amount && amount.compareTo(CAP) > 0
            ? "金额超过一百万，不能按这个结果提交" : null;
    }
}`;

const action = `@Component
public class PushToErp implements ServiceAction {
    @Override
    public String name() { return "app.push-erp"; }

    @Override
    public ExtensionInfo info() {
        return ExtensionInfo.of("推送 ERP").describedAs("把单据推给 ERP")
            .outcome("accepted", "对方接收")
            .outcome("rejected", "对方拒收");
    }

    /** 默认异步执行（作业），至少一次：要能重复执行，用 idempotencyKey 去重 */
    @Override
    public Result perform(Context context) {
        boolean ok = erp.push(context.getIdempotencyKey(), context.getInstance().getId(), context.getArgs());
        return Result.outcome(ok ? "accepted" : "rejected");
    }
}`;

const computed = `@Component
public class AmountLevel implements ComputedValue {
    private final BigDecimal threshold;

    public AmountLevel(@Value("\${app.amount-threshold:10000}") BigDecimal threshold) { this.threshold = threshold; }

    @Override
    public String name() { return "app.amountLevel"; }

    @Override
    public ExtensionInfo info() {
        return ExtensionInfo.of("金额档位").describedAs("high：金额达到阈值；其余 normal").valueType("text");
    }

    @Override
    public Object compute(Context context) {
        return context.getRead().apply("form.amount") instanceof BigDecimal amount && amount.compareTo(threshold) >= 0 ? "high" : "normal";
    }
}`;

const node = `@Component
public class CheckNode implements CustomNode {
    @Override
    public String type() { return "x-app-check"; }      // 自定义节点的类型以 x- 开头

    @Override
    public ExtensionInfo info() {
        return ExtensionInfo.of("外部核验").describedAs("到外部系统核验单据，当场给出结果")
            .param(ExtensionParam.select("system", "核验的系统", new Choice("erp", "ERP"), new Choice("crm", "CRM")).required())
            .outcome("pass", "通过")
            .outcome("fail", "不通过");
    }

    /** 当场给结果往下走；也可以 Result.waitFor(事件名) 停下来，等 signal 送来事件 */
    @Override
    public Result arrive(Context context) {
        return Result.proceed(checker.check(context.getAttributes().get("system")) ? "pass" : "fail");
    }
}`;

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="list" title="有哪些">
      <Tbl
        head={['接口', '做什么', '流程里怎么用']}
        rows={[
          [<Code>PeopleResolver</Code>, '自定义处理人来源（发起人的上级、项目经理）', '处理人 → 添加解析器'],
          [<Code>OutcomeGuard</Code>, '处理前的业务校验，不通过整个处理不做', '处理结果的"提交前检查"'],
          [<Code>ServiceAction</Code>, '自动节点、节点事件上的动作（调 ERP、发 MQ）', '自动处理节点的"动作"'],
          [<Code>ComputedValue</Code>, '计算值：条件里写 fn.名字，按你的规则算；阈值放你的配置里，调整不用重新发布流程', '条件构造器的"计算值"分组'],
          [<Code>CustomNode</Code>, '自定义节点类型（x-名字），当场给结果或停下来等事件', '设计器左边的节点列表'],
          [<Code>CountersignRule</Code>, '自定义会签完成规则', '会签方式'],
          [<Code>FieldType</Code>, '表单自定义字段类型的后端校验', '表单字段'],
          [<Code>TodoQueryCustomizer</Code>, '待办列表连业务表筛选、带出业务列', '—'],
          [<Code>CommandInterceptor</Code>, '命令前后（审计、改参数），与命令同一个事务', '—'],
          [<><Code>FlowListener</Code>、<Code>NotificationChannel</Code></>, '监听流转、发通知', '见 监听、通知与业务表'],
          [<Code>WorkCalendar</Code>, '自己的工作日历（默认读管理端维护的）', '—'],
        ]}
      />
      <Paragraph>
        声明成 Spring Bean 就按名字注册了（不用 Spring 时在 <Code>FlowEngine.builder(db)</Code> 上注册）。名字建议带上你的前缀（<Code>app.</Code>），流程定义里引用的名字没注册时，发布校验会报"没有注册"。
      </Paragraph>
    </Section>
    <Section id="designer" title="让设计器认得">
      <Paragraph>
        解析器、守卫、动作、计算值、自定义节点、会签规则、字段类型各有一个默认返回 null 的 <Code>info()</Code>。覆盖它给出显示名、说明、参数和可能的结果，设计器就从扩展目录（<Code>GET /flow/queries/extensions</Code>）读到它们：名字从下拉里选，参数按声明画成控件（必填标红），动作和自定义节点的出边能选声明的结果。不覆盖也能用，只是设计器里只显示名字、参数写 JSON。
      </Paragraph>
      <Shot src="build/esign/01-service.png" caption="示例注册的动作“推送外部系统”：显示名、说明、两个参数都来自 info()" />
      <Tbl
        head={['参数类型', '设计器里的控件']}
        rows={[
          ['text', '文字框'],
          ['number', '数字框'],
          ['boolean', '开关'],
          ['select（ExtensionParam.select）', '下拉，选项是 Choice'],
        ]}
      />
    </Section>
    <Section id="resolver" title="处理人解析器">
      <CodeBlock code={resolver} lang="java" />
      <Paragraph>
        定义里写成 <Code>{'{ "resolver": "app.leader", "args": { "level": 2 } }'}</Code>。返回的是用户编号；到达节点时解析一次，一个人直接落到他，多个人进收件箱。
      </Paragraph>
    </Section>
    <Section id="guard" title="提交前检查（守卫）">
      <CodeBlock code={guard} lang="java" />
    </Section>
    <Section id="action" title="自动处理的动作">
      <CodeBlock code={action} lang="java" />
      <Alert tone="info">
        动作默认异步：流转的事务提交后由作业执行，失败按节点上的设置重试，用完进异常处理。只写本库的表时可以设成同步（mode: sync），在流转的同一个事务里执行，失败整个回滚。
      </Alert>
    </Section>
    <Section id="computed" title="计算值">
      <CodeBlock code={computed} lang="java" />
      <Paragraph>
        条件里写 <Code>{'{ "field": "fn.app.amountLevel", "op": "eq", "value": "high" }'}</Code>。只能用在连线条件里（后端求值），表单的显示条件在前端算，不能用。
      </Paragraph>
    </Section>
    <Section id="node" title="自定义节点">
      <CodeBlock code={node} lang="java" />
      <Paragraph>节点 JSON 里的其他属性（这里的 system）原样交给你（getAttributes）；设计器按 info() 的参数画控件。</Paragraph>
    </Section>
  </DocPage>
));
