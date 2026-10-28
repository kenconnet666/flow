/** 教程 5：大额采购（子流程） */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Ops } from '../../site/Ops';
import { Shot } from '../../site/Shot';
import { Tbl } from '../../site/Tbl';

export const meta = {
  title: '教程 5：大额采购（子流程）',
  description: '大额采购先走一遍合同审批（复用教程 3 的流程），合同过了再请总经理确认。学会：子流程节点、传值、父子单据的关联。',
  order: 6,
};

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="goal" title="要画的流程">
      <Paragraph>提交 → 子流程（调用"合同审批"，等它结束）→ 总经理确认 → 结束。先做完教程 3。</Paragraph>
    </Section>
    <Section id="call" title="1. 子流程节点">
      <Ops
        items={[
          '新建流程：标识 bigbuy，名称 大额采购，表单 采购申请单，实例标题 大额采购：{form.title}',
          '审批 节点改名 总经理确认，处理人选 总经理',
          '点 提交，再点 子流程；调用的流程选 合同审批，方式 等子流程结束再往下',
          '传给子流程的值：点 同名的 4 个字段都带上——标题、金额、数量、财务意见原样带过去',
          '发布',
        ]}
      />
      <Shot src="build/bigbuy/01-call.png" caption="左边是子流程的字段，右边是这里的值；也可以填固定值" />
      <Tbl
        head={['方式', '效果']}
        rows={[
          ['等子流程结束再往下', '子流程结束的结果当作这个节点的结果（可以按结果分出边）；子流程的值可以写回来'],
          ['不等，直接往下', '发起子流程后马上往下走，两边各走各的'],
        ]}
      />
    </Section>
    <Section id="run" title="2. 跑一单">
      <Ops
        items={[
          '林小满发起 大额采购：生产线改造，金额 900000',
          '我发起的 里有两单：大额采购（停在子流程）与 合同：生产线改造（发起人同样是林小满）',
          '合同审批走完后，大额采购接着到总经理确认',
        ]}
      />
      <Shot src="build/bigbuy/02-parent.png" caption="父流程停在子流程节点上" />
      <Shot src="build/bigbuy/03-child.png" caption="子流程的详情里有“上级流程”，点它回到大额采购" />
      <Alert tone="info">父流程被撤回或终止时，进行中的子流程一起终止。一个列表里每一项各发起一个子流程（每个供应商一份报价）在 JSON 里写 forEach，见 开发 → 流程定义参考。</Alert>
    </Section>
  </DocPage>
));
