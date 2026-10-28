/** 待办与办理 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Paragraph, Strong } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Ops } from '../../site/Ops';
import { Shot } from '../../site/Shot';
import { Tbl } from '../../site/Tbl';

export const meta = {
  title: '待办与办理',
  description: '我的待办里有落到我的、和我所在收件箱里等人认领的；打开一单看表单，填意见，点同意或不同意。',
  order: 2,
  try: '/app/inbox',
};

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="inbox" title="我的待办">
      <Shot src="use/04-inbox.png" caption="我的待办：处理人一列写着名字的是落到人的；标着待认领的在收件箱里，谁先认领谁办" />
      <Tbl
        head={['标记', '意思']}
        rows={[
          ['新', '落到我、我还没打开过'],
          ['待认领', '派给了一个角色或部门（例如"财务"），在大家共用的收件箱里'],
          ['已超时', '过了这一步的截止时间'],
        ]}
      />
      <Paragraph>勾选几单可以 批量认领、批量同意。批量同意先让你确认，每单按它自己的"同意"处理；没办成的（例如这一步有必填字段）会报出条数，打开逐条办。</Paragraph>
    </Section>
    <Section id="handle" title="打开一单">
      <Shot src="use/05-item.png" caption="办理页：上面是单据信息与表单，下面是意见与处理结果；在收件箱里的单子可以先认领" />
      <Ops
        items={[
          '看表单：哪些字段你能改，由流程设计时给这一步配的权限决定（多数是只读，个别字段让你填，例如财务意见）',
          '写意见：有的结果要求必填意见（不同意、退回多半如此）',
          <>
            点处理结果。按钮下方一行写着每个结果会到哪一步，例如"通过 → 总经理审批（总经理）"
          </>,
          '暂存：意见和你填的字段先存着，下次打开还在',
        ]}
      />
      <Shot src="use/06-comment-required.png" caption="不同意（这里叫否决）要求写意见：没写时标在意见框上，不会提交" />
      <Alert tone="info">
        <Strong>收件箱里的单子</Strong>：直接点处理结果会顺带认领；先点 认领 则这单只在你名下，别人看不到了。认领后不想办可以 退回收件箱。
      </Alert>
    </Section>
    <Section id="designate" title="待我指定">
      <Paragraph>
        收件箱可以设负责人（例如财务主管）。单子在收件箱里太久没人认领时，可以按流程的设置转给负责人：它出现在负责人的 待我指定 里，负责人打开后 指定承办人，指定即生效。被指定的人觉得不该自己办，可以申请改派，由负责人处理。
      </Paragraph>
    </Section>
    <Section id="done" title="办过之后">
      <Paragraph>我已办 里能找到办过的单子。如果下一步还没人处理，可以 撤回这一步，单子回到你手里重新办。</Paragraph>
      <Shot src="use/11-withdraw-step.png" caption="我已办里打开刚办的单子：下一步的人还没动，可以撤回这一步" />
    </Section>
  </DocPage>
));
