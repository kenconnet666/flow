/** 我发起的与流程详情：看进度、催办、撤回、留言 @、抄送 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Shot } from '../../site/Shot';
import { Tbl } from '../../site/Tbl';

export const meta = {
  title: '我发起的与流程详情',
  description: '看单子走到哪了、谁在办；发起人可以催办、撤回；任何能看到这单的人都能留言、@ 人、抄送。',
  order: 4,
  try: '/app/started',
};

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="started" title="我发起的">
      <Shot src="use/12-started.png" caption="我发起的：状态一列直接写结果——已通过、未通过；进行中的写当前环节" />
      <Tbl
        head={['状态', '意思']}
        rows={[
          ['进行中', '还在流转，"当前环节"写着在哪一步'],
          ['已通过 / 未通过', '走完了，按结束时的结果'],
          ['已撤回', '发起人撤回了'],
          ['已终止 / 已暂停', '管理员终止了 / 暂停了（暂停的恢复后接着走）'],
        ]}
      />
    </Section>
    <Section id="detail" title="流程详情">
      <Shot src="use/13-detail.png" caption="详情：表单只读，下面是流程图（走过的、正在的、没到的）和流转记录；发起人可以催办、撤回" />
      <Paragraph>撤回：默认在有人处理之前可以撤回，撤回后打开着的待办都取消；流程也可以设成"有人认领之前"或"不能撤回"。催办会提醒当前的处理人，同一单一小时内只催一次。</Paragraph>
    </Section>
    <Section id="comment" title="留言与 @">
      <Shot src="use/14-comment.png" caption="留言会记进流转记录；@ 的人会收到通知" />
    </Section>
    <Section id="cc" title="抄送">
      <Shot src="use/15-cc.png" caption="抄送：选人、写附言" />
      <Paragraph>被抄送的人在 抄送我的 里看到，打开后标为已读。流程也可以在某一步自动抄送（抄送节点）。</Paragraph>
      <Shot src="use/16-cc-list.png" caption="抄送我的：未读的有标记" />
    </Section>
  </DocPage>
));
