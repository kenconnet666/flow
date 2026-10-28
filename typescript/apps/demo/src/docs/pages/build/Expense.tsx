/** 教程 1：报销（条件分支） */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Ops } from '../../site/Ops';
import { Shot } from '../../site/Shot';

export const meta = {
  title: '教程 1：报销（条件分支）',
  description: '财务审核，金额五千及以上再给总经理批。学会：流程属性、按角色选处理人、条件分支、默认出边、发布、跑一单。',
  order: 2,
  try: '/app/admin/processes/new',
};

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="goal" title="要画的流程">
      <Paragraph>提交 → 财务审核（财务角色，谁先认领谁办）→ 金额 ≥ 5000 时总经理审批 → 结束；不到 5000 的财务审核完就结束。</Paragraph>
      <Alert tone="info">本教程先借用示例里的"采购申请单"（标题、金额、数量、财务意见）。表单章节会画一张真正的"报销单"换上去。</Alert>
    </Section>
    <Section id="process" title="1. 流程属性">
      <Ops
        items={[
          '右上角的当前用户切到 总经理，进 流程管理 → 新建流程',
          '流程标识填 expense（发起地址、接口里用它，发布后不能改），名称填 报销',
          '表单选 采购申请单',
          '实例标题填 报销：{form.title}——花括号里写字段，待办列表里显示成"报销：去上海见客户"',
        ]}
      />
      <Shot src="build/expense/01-process.png" caption="什么都没选时，右边是流程属性" />
    </Section>
    <Section id="finance" title="2. 财务审核">
      <Ops
        items={[
          '点画布上的 审批 节点，名称改成 财务审核',
          '处理人：去掉"发起人"，点 选人 → 角色 → 勾 财务 → 确定',
          '处理人是角色时，单子进这个角色所有人的收件箱，谁先认领谁办',
        ]}
      />
      <Shot src="build/expense/02-finance.png" caption="处理人可以是人、角色、部门，也可以多选" />
    </Section>
    <Section id="branch" title="3. 条件分支">
      <Ops
        items={[
          '财务审核还选中着，点左边 条件分支：它插在财务审核和结束之间',
          '条件分支选中着，点 人工审批：多了一条分支，名称改成 总经理审批，处理人选 总经理 角色',
        ]}
      />
      <Shot src="build/expense/03-branch.png" caption="条件分支现在有两条出边：到总经理审批、到结束" />
      <Paragraph>右下出现两条问题：两条出边都没有条件，也都不是默认出边。下面给它们配上。</Paragraph>
    </Section>
    <Section id="condition" title="4. 配条件">
      <Ops
        items={[
          '点 条件分支 → 总经理审批 这条线',
          '条件：添加条件 → 字段选 金额，比较方式选 大于等于，值填 5000',
          '线上的文字填 ≥ 5000（画布与流程图上显示）',
        ]}
      />
      <Shot src="build/expense/04-condition.png" caption="条件可以有多条（全部满足 / 任一满足），也可以分组" />
      <Ops items={['点 条件分支 → 结束 这条线，打开 默认出边（其余条件都不满足时走这条），线上的文字填 其余']} />
      <Shot src="build/expense/05-default.png" caption="设了默认出边，问题清零" />
    </Section>
    <Section id="publish" title="5. 发布">
      <Ops items={['点 自动排版 整理一下', '发布说明填 首次发布，点 发布']} />
      <Shot src="build/expense/06-published.png" caption="发布成功：这是 v1，发起页里出现了报销" />
    </Section>
    <Section id="run" title="6. 跑一单">
      <Ops
        items={[
          '切到 林小满（发起人），发起 → 报销，标题 去上海见客户，金额 8000；提交前看到"提交后到：财务审核"',
          '切到 周启航（财务），我的待办里有这一单（待认领）；打开，处理结果下面写着同意会到总经理审批',
          '周启航点 同意；切到 总经理，同意',
          '回到林小满 → 我发起的：已通过',
        ]}
      />
      <Shot src="build/expense/07-start.png" caption="发起时就能看到第一步交给谁" />
      <Shot src="build/expense/08-finance-item.png" caption="8000 ≥ 5000，同意之后到总经理审批" />
      <Shot src="build/expense/09-done.png" caption="走完了：已通过" />
      <Paragraph>再发起一单 3000 的试试：财务同意后直接结束，总经理不会收到。</Paragraph>
    </Section>
  </DocPage>
));
