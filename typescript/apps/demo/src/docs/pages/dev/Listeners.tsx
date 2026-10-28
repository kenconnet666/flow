/** 监听、通知与业务表 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Code, CodeBlock, Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Tbl } from '../../site/Tbl';

export const meta = {
  title: '监听、通知与业务表',
  description: '流转的每一步都写一条流转记录。FlowListener 听这些记录同步业务表、发消息；NotificationChannel 把引擎算好的通知发出去；节点的业务信息（meta）让你按节点配业务状态。',
  order: 7,
};

const listener = `@Component
public class OrderStatusSync implements FlowListener {
    @Override
    public Phase phase() { return Phase.IN_TRANSACTION; }          // 和流转同一个事务：业务表与流程同成同败

    @Override
    public Set<String> types() { return Set.of("node.entered", "instance.finished"); }

    @Override
    public Set<String> processes() { return Set.of("purchase"); }

    @Override
    public void on(FlowNotice notice) {
        String status = (String) notice.getNodeMeta().get("bizStatus");   // 节点上配的业务状态
        if (status != null) orders.updateStatus(notice.getInstance().getBusinessKey(), status);
    }
}`;

const channel = `@Component
public class MailChannel implements NotificationChannel {
    @Override
    public void send(Notification n) {
        String title = n.getInstance().getTitle();
        switch (n.getKind()) {
            case Notification.ARRIVED -> mail.send(n.getRecipients(), "新待办：" + title, n.getTemplate());
            case Notification.FINISHED -> mail.send(n.getRecipients(), "已结束：" + title, n.getTemplate());
            default -> { }
        }
    }

    @Override
    public boolean reliable() { return true; }      // 走发件箱：邮件服务一时不可用也不丢
}`;

const meta_ = `flow:
  designer:
    node-meta:
      - key: bizStatus
        label: 业务状态
        type: select
        description: 单据停在这个节点时列表上显示的状态
        options:
          - { value: accepting, label: 受理中 }
          - { value: reviewing, label: 审核中 }
          - { value: closed, label: 已办结 }`;

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="phases" title="监听的三种时机">
      <Tbl
        head={['phase()', '什么时候调', '抛错', '适合']}
        rows={[
          [<Code>AFTER_COMMIT</Code>, '命令的事务提交之后（默认）', '只记日志，不影响流转', '发站内信这类丢一条也无妨的'],
          [<Code>IN_TRANSACTION</Code>, '写流转记录的那一刻，同一个事务', '整个命令回滚（可以用来拒绝）', '同步本库的业务表'],
          [<Code>RELIABLE</Code>, '同事务记成作业，提交后马上投', '按 1、4、9、16 分钟重试，五次失败进异常处理', '发 MQ、同步外部系统'],
        ]}
      />
      <CodeBlock code={listener} lang="java" />
      <Alert tone="info">
        可靠投递是"至少一次"：同一条可能收到两次，按 notice.getEvent().getId() 去重。可靠的监听按名字找回（listenerName()，默认类名），改了类名时还没投完的会失败、进异常处理。
      </Alert>
    </Section>
    <Section id="events" title="流转记录的种类">
      <Tbl
        head={['分组', '种类']}
        rows={[
          ['实例', 'instance.started、finished、withdrawn、terminated、suspended、resumed、jumped、migrated、dataChanged、commented、cc'],
          ['节点', 'node.entered、left、skipped、overdue；call.started'],
          ['待办', 'workItem.created、claimed、completed、canceled、transferred、delegated、signersAdded、signersRemoved、released、reassigned、reminded、overdue、assigned、unclaimed、reassignRequested、withdrawn、resumed'],
          ['作业与事件', 'job.failed、job.skipped、signal.received、signal.ignored'],
        ]}
      />
    </Section>
    <Section id="notify" title="通知">
      <Paragraph>引擎算好"发给谁、哪一种、用哪个模板"，你的渠道负责真正发出去（站内信、邮件、企业微信、钉钉）：</Paragraph>
      <Tbl
        head={['种类', '默认收件人']}
        rows={[
          ['arrived 待办到达', '处理人或收件箱里的候选人'],
          ['reminded 催办', '当前处理人'],
          ['dueSoon 即将到期、overdue 已超时', '当前处理人；超时另发收件箱的负责人'],
          ['cc 抄送', '抄送的人'],
          ['returned 被退回', '退回的目标处理人'],
          ['mentioned 留言里 @', '被 @ 的人'],
          ['finished 流程结束', '发起人'],
        ]}
      />
      <CodeBlock code={channel} lang="java" />
      <Paragraph>
        流程定义可以按流程（settings.notify）或按节点改：换模板、加收件人、某种不发，例如 <Code>{'"notify": { "arrived": { "template": "purchase-arrived" }, "dueSoon": false }'}</Code>。模板只是一个名字，内容与渲染由渠道负责。
      </Paragraph>
    </Section>
    <Section id="meta" title="节点的业务信息">
      <Paragraph>
        "单据停在这个节点时业务表显示什么状态""算哪个环节的超时"——这类每个节点要配、引擎不解释的值写在节点的 <Code>meta</Code> 里。在配置里声明有哪些字段，设计器的节点面板就多一块"业务信息"；你的监听在事务内读到达节点的 meta 同步业务表。
      </Paragraph>
      <CodeBlock title="application.yml" code={meta_} lang="yaml" />
    </Section>
  </DocPage>
));
