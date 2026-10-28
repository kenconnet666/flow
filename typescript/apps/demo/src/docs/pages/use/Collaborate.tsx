/** 协作：转办、委派、加签 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Shot } from '../../site/Shot';
import { Tbl } from '../../site/Tbl';

export const meta = {
  title: '协作：转办、委派、加签',
  description: '自己办不了、要别人帮忙、要多几个人看——办理页上的转办、委派、加签分别对应这三种情况。',
  order: 3,
  try: '/app/inbox',
};

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="which" title="用哪一个">
      <Tbl
        head={['操作', '什么时候用', '之后']}
        rows={[
          ['转办', '这单不该我办 / 我出差', '交给别人，我不再参与'],
          ['委派', '请别人先看看、给个意见', '他办完回到我手里，由我决定'],
          ['加签', '要多几个人一起审', '前加签：他们先办，再回到我；后加签：我办完他们再办；并加：一起办'],
        ]}
      />
      <Alert tone="info">哪一步能转办、委派、加签由流程设计时决定（节点属性里的"允许的操作"），没开的按钮不显示。</Alert>
    </Section>
    <Section id="transfer" title="转办">
      <Shot src="use/07-transfer.png" caption="选交给谁、写一句说明；说明会记进流转记录，对方打开时能看到" />
    </Section>
    <Section id="delegate" title="委派">
      <Shot src="use/08-delegate.png" caption="委派给许知远，请他核一下报价" />
      <Paragraph>被委派的人打开单子时，页面会说明"某某请你给意见"：他选的结果和写的意见记进流转记录，但流程怎么往下走仍由原来的人决定。</Paragraph>
      <Shot src="use/09-opinion.png" caption="被委派的人看到的提示：结果只是意见" />
    </Section>
    <Section id="add-sign" title="加签">
      <Shot src="use/10-add-sign.png" caption="加签：选人、选方式（前加签、后加签、并加）、写说明" />
      <Paragraph>加错了可以减签（还没办的被加签人）。所有加签的人办完，单子回到你手里。</Paragraph>
    </Section>
  </DocPage>
));
