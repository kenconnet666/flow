/** 发起与草稿 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Ops } from '../../site/Ops';
import { Shot } from '../../site/Shot';

export const meta = {
  title: '发起与草稿',
  description: '在发起页挑一个流程、填表提交；没填完先暂存，下次在"我的草稿"里接着填。',
  order: 1,
};

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="start" title="发起一单">
      <Ops
        items={[
          '左侧点 发起，列出你能发起的流程（流程按分类分组；停用的、你不在发起范围里的不显示）',
          '点一个流程，填表：带红星的是必填；有的字段要先选了别的才出现（例如费用类别选"差旅"才要填同行人）',
          '提交按钮上方一行写着"提交后到：……"——提交后会到哪一步、交给谁，提交前就能确认',
          '点 提交；成功后回到 我的待办，顶上提示"已发起"',
        ]}
      />
      <Shot src="use/02-start-form.png" caption="填表时就能看到提交后到哪一步：金额一万六，会先到财务复核（进收件箱，财务里谁先认领谁办）" />
      <Alert tone="info">有的流程第一步会让你选下一步的处理人（例如"经理审批"由你挑一位），提交时会弹出选人框。</Alert>
    </Section>
    <Section id="draft" title="暂存与草稿">
      <Paragraph>填到一半要离开，点 暂存。草稿只有自己看得到，不会进任何人的待办。</Paragraph>
      <Shot src="use/03-drafts.png" caption="发起页顶上是我的草稿：接着填，或者放弃" />
      <Paragraph>暂存不校验必填，提交时才校验。附件没传完时不能提交，会提示"附件还在上传"。</Paragraph>
    </Section>
  </DocPage>
));
