/** 教程 6：外部签约（自动处理与等待事件） */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, CodeBlock, Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Ops } from '../../site/Ops';
import { Shot } from '../../site/Shot';

export const meta = {
  title: '教程 6：外部签约（自动处理与等待事件）',
  description: '提交后自动把单据推给电子签系统，等对方回调"已签署"再结束。学会：自动处理节点、等待事件节点、外部系统怎么送事件。',
  order: 7,
  try: '/app/admin/processes/new',
};

const signal = `curl -X POST http://localhost:8080/flow/commands/signal \\
  -H 'content-type: application/json' \\
  -H 'x-user: 1004' \\
  -d '{ "instanceId": "<实例编号>", "event": "esign.signed" }'`;

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="goal" title="要画的流程">
      <Paragraph>提交 → 自动处理（推送外部系统）→ 等签署回执（等事件 esign.signed）→ 结束。整个流程没有人工节点。</Paragraph>
    </Section>
    <Section id="service" title="1. 自动处理">
      <Ops
        items={[
          '新建流程：标识 esign，名称 外部签约，表单 采购申请单，实例标题 签约：{form.title}',
          '选中 审批 节点，删除',
          '点 提交，再点 自动处理；动作 选 推送外部系统',
        ]}
      />
      <Shot src="build/esign/01-service.png" caption="动作由接入方用 Java 注册（示例里的 demo.push），下面的参数是动作自己声明的" />
      <Paragraph>失败后重试几次、重试用完后怎么办（等管理员处理 / 整个流程暂停）也在这里配。重试用完的会进 管理 → 异常处理。</Paragraph>
    </Section>
    <Section id="receive" title="2. 等待事件">
      <Ops items={['自动处理选中着，点 等待事件；名称 等签署回执，事件名 esign.signed', '发布']} />
      <Shot src="build/esign/02-receive.png" caption="事件名是和外部系统约定的名字" />
    </Section>
    <Section id="run" title="3. 跑一单">
      <Ops items={['林小满发起 外部签约：供应商框架协议', '自动处理在后台执行（异步作业），几秒后流程停在 等签署回执']} />
      <Shot src="build/esign/03-waiting.png" caption="自动处理已完成，正在等事件" />
      <Paragraph>外部系统签完后调引擎的接口送来事件，流程就往下走：</Paragraph>
      <CodeBlock lang="bash" code={signal} />
      <Alert tone="info">
        也可以不给实例编号，改给 businessKey（发起时传的业务编号）。事件可以带 outcome（等待事件节点据此分出边）和 data（写进表单或流程变量）。实例没在等这个事件时只记一条流转记录、不报错，外部系统可以放心重发。
      </Alert>
    </Section>
  </DocPage>
));
