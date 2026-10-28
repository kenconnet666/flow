/** 教程 3：合同审批（退回、上一步选人、抄送、自动跳过） */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Ops } from '../../site/Ops';
import { Shot } from '../../site/Shot';
import { Tbl } from '../../site/Tbl';

export const meta = {
  title: '教程 3：合同审批（退回与选人）',
  description: '发起人起草、法务审核（可以退回修改）、抄送财务、由法务挑一位经理审批。学会：处理结果的种类、退回、上一步选人、抄送节点、自动跳过。',
  order: 4,
};

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="goal" title="要画的流程">
      <Paragraph>提交 → 起草（发起人；发起时自动跳过，被退回才轮到他）→ 法务审核（同意 / 不同意 / 退回修改）→ 抄送财务 → 经理审批（法务从总经理、沈若水里挑一位）→ 结束。</Paragraph>
    </Section>
    <Section id="skip" title="1. 起草与自动跳过">
      <Ops
        items={[
          '新建流程：标识 contract，名称 合同审批，表单 采购申请单，实例标题 合同：{form.title}',
          '流程属性里勾 自动跳过：处理人就是发起人时',
          '审批 节点改名 起草（处理人保持"发起人"）；处理结果的 同意 改成 提交，删掉 不同意',
        ]}
      />
      <Shot src="build/contract/01-auto-skip.png" caption="发起时起草这一步直接过；法务退回到起草时，才真的进发起人的待办" />
      <Paragraph>为什么要一个"起草"节点：退回只能退到人工节点，想让法务把合同退回给发起人改，前面就得有一个发起人处理的节点。</Paragraph>
    </Section>
    <Section id="return" title="2. 法务审核与退回">
      <Ops
        items={[
          '起草后面加 人工审批，改名 法务审核，处理人选 许知远',
          '处理结果里 添加结果，按钮上的字填 退回修改，种类选 退回，勾 处理时选退回到哪一步',
        ]}
      />
      <Shot src="build/contract/02-return.png" caption="处理结果：每个结果一个按钮，种类决定它的作用" />
      <Tbl
        head={['种类', '作用']}
        rows={[
          ['普通 / 通过 / 驳回', '沿这个结果的出边往下走；区别在按钮的样子（驳回是红色）'],
          ['退回', '回到前面的人工节点，落到那个节点原来的处理人；勾了"处理时选"由处理人当场挑退到哪一步（不要出边），否则沿出边'],
          ['提级', '沿出边交给目标节点的收件箱'],
          ['终止', '整个流程立即结束，不需要出边'],
        ]}
      />
      <Alert tone="info">"意见必填"打开后，点这个按钮必须写意见；"提交前检查"可以挂一个接入方注册的守卫（例如预算是否够）。</Alert>
    </Section>
    <Section id="cc" title="3. 抄送财务">
      <Ops items={['法务审核选中着，点 抄送；抄送给 → 选人 → 角色 → 财务']} />
      <Shot src="build/contract/03-cc.png" caption="抄送节点不等人：抄送完立即往下走，财务在“抄送我的”里看到" />
    </Section>
    <Section id="pick" title="4. 上一步选人">
      <Ops
        items={[
          '抄送后面加 人工审批，改名 经理审批',
          '处理人：去掉"发起人"，勾 上一步的人选，候选人选 总经理、沈若水，最多选 1 人',
        ]}
      />
      <Shot src="build/contract/04-pick.png" caption="上一步的人选：法务点同意时弹出选人框，只能从候选人里挑" />
      <Ops items={['自动排版，发布']} />
      <Shot src="build/contract/05-published.png" caption="画好的合同审批" />
    </Section>
    <Section id="run" title="5. 跑一单">
      <Ops
        items={[
          '林小满发起 合同审批：年度维保合同。起草自动跳过，直接到法务',
          '许知远写意见 付款条款要改，点 退回修改，选退回到 起草',
          '林小满的待办里出现 起草，改好点 提交，又到法务',
          '许知远点 同意，弹出"选择下一步的处理人"，挑 沈若水',
          '沈若水同意，结束；财务在抄送我的里能看到这一单',
        ]}
      />
      <Shot src="build/contract/06-return-dialog.png" caption="退回时选退回到哪一步：列出前面走过的人工节点" />
      <Shot src="build/contract/07-pick-dialog.png" caption="同意时选经理审批的处理人" />
      <Shot src="build/contract/08-timeline.png" caption="流转记录：退回、重新提交、抄送、选人都记在里面" />
    </Section>
  </DocPage>
));
