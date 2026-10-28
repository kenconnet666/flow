/** 教程 4：出差申请（包容分支与定时） */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Code, Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Ops } from '../../site/Ops';
import { Shot } from '../../site/Shot';

export const meta = {
  title: '教程 4：出差申请（包容分支与定时）',
  description: '部门审批后，金额大的要财务看、人多的要行政看，两个条件可能同时成立；汇合后等一会儿再结束。学会：包容分支、多条件、定时节点。',
  order: 5,
  try: '/app/admin/processes/new',
};

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="goal" title="要画的流程">
      <Paragraph>
        提交 → 部门审批 → 包容分支：金额 ≥ 3000 到财务审核、数量 ≥ 2 到行政审批（都成立就两处都去），其余直接到汇合 → 汇合 → 定时等 1 分钟 → 结束。
      </Paragraph>
      <Alert tone="info">条件分支只走第一条成立的；包容分支把成立的都走一遍，汇合时只等实际走了的那几条。</Alert>
    </Section>
    <Section id="branches" title="1. 包容分支">
      <Ops
        items={[
          '新建流程：标识 trip，名称 出差申请，表单 采购申请单，实例标题 出差：{form.title}',
          '审批 节点改名 部门审批，处理人选 总经理',
          '部门审批选中着，点 包容分支；再点 人工审批，改名 财务审核，处理人 周启航',
          '点 包容分支，再点 人工审批，改名 行政审批，处理人 许知远',
          '点 财务审核，再点 包容分支，改名 汇合',
        ]}
      />
    </Section>
    <Section id="wire" title="2. 接线与条件">
      <Ops
        items={[
          '行政审批 → 结束：同意 这条线改接到 汇合',
          '包容分支 → 结束 这条线改接到 汇合，打开 默认出边，线上的文字填 其余',
          '包容分支 → 财务审核：条件 金额 大于等于 3000',
          '包容分支 → 行政审批：条件 数量 大于等于 2',
        ]}
      />
      <Shot src="build/trip/01-conditions.png" caption="包容分支：每条线一个条件，成立的都走" />
    </Section>
    <Section id="timer" title="3. 定时">
      <Ops items={['点 汇合，再点 定时；等多久填 1，单位选 分钟', '发布']} />
      <Shot src="build/trip/02-timer.png" caption="定时节点：到了时间自动往下走" />
      <Paragraph>
        定时也可以按工作日算（流程的工作日历，见 管理 → 工作日历），或者等到表单里的某个日期——在 JSON 里写 <Code>{'"wait": { "until": { "ref": "form.signDate" } }'}</Code>。
      </Paragraph>
      <Shot src="build/trip/03-published.png" caption="画好的出差申请" />
    </Section>
    <Section id="try" title="4. 试一试">
      <Ops
        items={[
          '金额 5000、数量 1：部门审批后只到财务审核',
          '金额 5000、数量 3：财务审核、行政审批同时有待办，两边都办完才汇合',
          '金额 500、数量 1：部门审批后直接汇合，一分钟后结束',
        ]}
      />
    </Section>
  </DocPage>
));
