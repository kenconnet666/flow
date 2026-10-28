/** 教程 2：采购评审（并行与会签） */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Ops } from '../../site/Ops';
import { Shot } from '../../site/Shot';
import { Tbl } from '../../site/Tbl';

export const meta = {
  title: '教程 2：采购评审（并行与会签）',
  description: '技术评审和财务评审同时进行，财务里每个人都要同意（会签），两边都过了再给总经理。学会：并行、汇合、会签、改接连线。',
  order: 3,
  try: '/app/admin/processes/new',
};

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="goal" title="要画的流程">
      <Paragraph>提交 → 并行（技术评审：许知远；财务评审：财务全体会签）→ 汇合 → 总经理审批 → 结束。</Paragraph>
    </Section>
    <Section id="draw" title="1. 两条并行的分支">
      <Ops
        items={[
          '新建流程：标识 review，名称 采购评审，表单 采购申请单，实例标题 采购评审：{form.title}',
          '审批 节点改名 技术评审，处理人选 许知远',
          '点 提交 节点，再点左边 并行：并行插在提交和技术评审之间',
          '并行选中着，点 人工审批：多了一条分支，改名 财务评审，处理人选 财务 角色',
          '会签 选 全部通过',
        ]}
      />
      <Shot src="build/review/01-countersign.png" caption="会签：处理人是角色或多个人时，每人一个待办，按规则决定这一步过没过" />
      <Tbl
        head={['会签方式', '这一步什么时候算过']}
        rows={[
          ['不会签', '处理人是多人时进收件箱，一个人办了就算'],
          ['全部通过', '每人都同意；有一人不同意就按不同意走'],
          ['一人通过即可', '有一人同意就过，其余的待办取消'],
          ['依次审批', '按顺序一个接一个，全部同意才过'],
          ['按比例 / 按票数通过', '同意的比例或票数达到设定值就过'],
        ]}
      />
    </Section>
    <Section id="join" title="2. 汇合">
      <Ops
        items={[
          '点 技术评审，再点 并行：这个并行插在技术评审之后，改名 汇合——成对的第二个并行负责等分支到齐',
          '财务评审现在还连着结束：点 财务评审 → 结束：同意 这条线，在"连到"里改成 汇合',
        ]}
      />
      <Shot src="build/review/02-join.png" caption="技术评审后面加了汇合；财务评审还没接过来，右下提示分叉没有配对的汇合" />
      <Shot src="build/review/03-retarget.png" caption="把财务评审的线改接到汇合" />
      <Alert tone="warning">并行要成对：一个分叉配一个汇合，中间的分支不能连到外面去。没配好时校验会提示。</Alert>
    </Section>
    <Section id="boss" title="3. 总经理与发布">
      <Ops items={['点 汇合，再点 人工审批，改名 总经理审批，处理人选 总经理', '自动排版，发布']} />
      <Shot src="build/review/04-published.png" caption="画好的采购评审" />
    </Section>
    <Section id="run" title="4. 跑一单">
      <Ops
        items={[
          '林小满发起 采购评审，标题 服务器采购，金额 120000：提交后同时到技术评审、财务评审',
          '许知远同意；周启航同意——财务还有沈若水没办，汇合还在等',
          '沈若水同意，两边到齐，到总经理审批',
        ]}
      />
      <Shot src="build/review/05-start.png" caption="提交后到两处" />
      <Shot src="build/review/06-midway.png" caption="中途的流程图：技术评审已过（绿色），财务评审还在办（蓝色），汇合与总经理还没到（虚线）" />
    </Section>
  </DocPage>
));
