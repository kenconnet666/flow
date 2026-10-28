/** 异常处理、工作日历、统计 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Code, Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Shot } from '../../site/Shot';
import { Tbl } from '../../site/Tbl';

export const meta = {
  title: '异常、日历与统计',
  description: '自动处理重试用完后的异常怎么处理；时限按哪些天算；各流程、各节点、各人的处理情况。',
  order: 3,
};

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="incidents" title="异常处理">
      <Paragraph>
        自动处理节点（推送 ERP、调外部接口）失败会按设置重试；重试用完还失败，就成了一条异常，列在这里，流程停在那个节点（设置成"整个流程暂停"的会暂停整单）。
      </Paragraph>
      <Shot src="admin/11-incidents.png" caption="异常处理：最新的在前，能看到错误信息" />
      <Tbl
        head={['操作', '效果']}
        rows={[
          ['重试', '外部系统恢复后点它，重新排队执行'],
          ['跳过', '不再执行这个动作，选一个结果往下走（例如当作"对方接收"）'],
          ['终止', '打开流程详情终止整单'],
          ['放弃投递', '发件箱里发不出去的流转记录、通知不再投递给监听器或通知渠道'],
        ]}
      />
    </Section>
    <Section id="calendars" title="工作日历">
      <Paragraph>
        时限、定时写成工作日（例如"2 个工作日内处理"）时按日历算：一周哪几天上班，加上节假日与调休。流程定义的 <Code>settings.calendar</Code> 写用哪份日历，不写用 default；一份都没建时按周一到周五算。
      </Paragraph>
      <Shot src="admin/12-calendars.png" caption="工作日历：名字、时区、上班的日子、节假日与调休" />
    </Section>
    <Section id="stats" title="统计">
      <Paragraph>各流程的单子数（进行中、已完成、终止与撤回）；选一个流程看各人工节点处理了多少、平均停留多久、超时的比例，以及每个处理人的工作量。节点与处理人默认按最近 30 天算。</Paragraph>
      <Shot src="admin/13-stats.png" caption="统计" />
    </Section>
  </DocPage>
));
