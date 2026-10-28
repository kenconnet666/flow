/** 表单后端代码（FormHandler） */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Code, CodeBlock, Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Shot } from '../../site/Shot';
import { Tbl } from '../../site/Tbl';

export const meta = {
  title: '表单后端代码',
  description: '表单要查预算、算合计、写业务表、给前端一个查询接口时，在表单编辑器的"后端代码"页签写一个 Java 类，跟表单一起发布。',
  order: 8,
};

const handler = `import org.dpdns.zerodep.flow.core.spi.*;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.util.Map;

public class ExpenseForm implements FormHandler {
    @Autowired BudgetService budgets;          // 你自己的 Spring Bean，照常注入
    @Autowired ExpenseMapper expenses;

    /** 发起页打开时带出初始值 */
    @Override
    public FormValues load(FormContext ctx) {
        return FormValues.of(Map.of("department", budgets.departmentOf(ctx.getActor())));
    }

    /** 清单与字段权限校验通过之后：只有后端能做的校验 */
    @Override
    public FieldErrors validate(FormValues values, FormContext ctx) {
        return values.decimal("amount").compareTo(budgets.remaining(values.text("department"))) > 0
            ? FieldErrors.of("amount", "超出部门预算")
            : FieldErrors.none();
    }

    /** 后端算的字段，覆盖前端交上来的值 */
    @Override
    public FormValues compute(FormValues values, FormContext ctx) {
        return FormValues.of(Map.of("level", values.decimal("amount").compareTo(new BigDecimal("5000")) >= 0 ? "high" : "normal"));
    }

    /** 和流转同一个事务写业务表；抛错整个回滚 */
    @Override
    public void submit(FormValues values, FormContext ctx) {
        if (ctx.isStart()) expenses.insert(ctx.getInstanceId(), values.asMap());
        else expenses.update(ctx.getInstanceId(), values.asMap());
    }

    /** 给前端调的接口：表单代码里 context.call('remaining', { department }) */
    @Override
    public Map<String, FormAction> actions() {
        return Map.of("remaining", (args, ctx) -> budgets.remaining((String) args.get("department")));
    }
}`;

const front = `export default defineFlowForm(fields, ({ form, context }) => {
  let remaining = _state<string | null>(null);
  watch(() => form.values.department, async (department) => {
    remaining = department ? String(await context.call('remaining', { department })) : null;
  });
  return () => (
    <form.Form>
      <form.Field name="department"><TextField /></form.Field>
      <form.Field name="amount" description={remaining ? \`部门预算还剩 \${remaining}\` : undefined}>
        <NumberField />
      </form.Field>
    </form.Form>
  );
});`;

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="when" title="什么时候需要">
      <Tbl
        head={['需求', '用什么']}
        rows={[
          ['必填、长度、范围、选项、显示与必填条件、每个节点能看能改哪些字段', '表单设计器就够，不用写代码（前后端按同一份规则校验）'],
          ['查预算够不够、单号查重这类要查库的校验', 'FormHandler.validate'],
          ['合计、档位这类服务端说了算的字段', 'FormHandler.compute'],
          ['提交时写自己的业务表（和流转同成同败）', 'FormHandler.submit'],
          ['发起页带出默认值（申请人的部门）', 'FormHandler.load'],
          ['填表时查点东西（剩余预算、供应商信息）', 'FormHandler.actions，前端 context.call'],
          ['跨很多流程的同一种规则（"金额超过一百万不能同意"）', '扩展点 OutcomeGuard，别写在每张表单里'],
          ['按节点同步业务状态', '监听 FlowListener'],
        ]}
      />
    </Section>
    <Section id="code" title="怎么写">
      <Paragraph>
        在表单编辑器的"后端代码"页签写一个 <Code>public</Code> 类实现 <Code>FormHandler</Code>（不写 package），每个方法都可选。能试编译，跟表单一起发布成一个版本。
      </Paragraph>
      <Shot src="forms/09-backend.png" caption="后端代码页签：写好点试编译，错误标在行上" />
      <CodeBlock code={handler} lang="java" />
      <Tbl
        head={['方法', '时机']}
        rows={[
          [<Code>load</Code>, '发起页打开时：带出初始值（盖在清单的默认值上）'],
          [<Code>validate</Code>, '发起、处理提交时，清单与字段权限校验通过之后；返回按字段的错误，整个提交不做'],
          [<Code>compute</Code>, 'validate 通过之后：返回的值覆盖提交的（只写要覆盖的字段），可以写任何字段'],
          [<Code>submit</Code>, '最后，和流转在同一个事务里'],
          [<Code>actions</Code>, '前端随时调；能看这个实例的人才能调'],
        ]}
      />
      <Paragraph>暂存只做清单校验，不调这里。FormContext 里有操作人、流程标识、实例编号（load 时还没有）、当前节点、是不是发起。</Paragraph>
    </Section>
    <Section id="front" title="前端调 actions">
      <CodeBlock title="表单代码" code={front} lang="tsx" />
    </Section>
    <Section id="safety" title="安全">
      <Alert tone="warning">
        能发布表单就等于能在服务器上跑 Java。生产环境不想这样时设 <Code>flow.forms.server-code: false</Code>，关掉在线写后端代码；同样的逻辑写成扩展点（随应用发布、走代码评审）。
      </Alert>
      <Paragraph>后端代码在发布时编译，类由 Spring 创建（AutowireCapableBeanFactory），所以 @Autowired 能注入；不用 Spring 时要有无参构造。</Paragraph>
    </Section>
  </DocPage>
));
