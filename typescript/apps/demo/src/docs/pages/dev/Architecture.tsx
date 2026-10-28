/** 原理与模块 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Code, CodeBlock, ListItem, OrderedList, Paragraph, Strong } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Tbl } from '../../site/Tbl';

export const meta = {
  title: '原理与模块',
  description: '引擎由哪些包组成、一次办理在前后端之间怎么走、数据存在哪些表、作业做什么。接入前先读这一页。',
  order: 1,
};

const picture = `你的前端（Vite + Vue 3）                          你的后端（Spring Boot 3）
  createUi({ http, lang, router })                 flow-spring（自动配置）
  页面里放 flow-ui 的组件  ── /flow/queries/* ──▶    FlowEngine ── easy-query ── MySQL（flow_* 表）
  （StartForm、FlowInbox、WorkItemDetail…）  /flow/commands/*   你实现：Actor、OrgDirectory、ActorProvider
                                                    可选：PermissionPolicy、ViewPolicy、扩展点`;

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="picture" title="一张图">
      <CodeBlock code={picture} />
      <Paragraph>
        引擎是嵌进你后端的一个库，不是单独部署的服务：表建在你的 MySQL 里，命令在你的事务里跑，用户、部门、角色查你的组织架构。前端是一组 Vue 组件，放进你自己的页面。
      </Paragraph>
    </Section>
    <Section id="packages" title="包">
      <Tbl
        head={['包', '放在哪', '做什么']}
        rows={[
          [<Code>flow-model</Code>, 'Java；TS 的 @zerodep-dev/flow-model', '流程定义与表单字段清单的模型、发布校验、条件求值、字段权限的计算。前后端各一份，用同一套规格用例（spec/）核对'],
          [<Code>flow-core</Code>, 'Java', '引擎：命令（发起、处理、协作、管理员操作）、查询（待办、详情、列表）、作业、扩展点（spi 包）。不依赖 Spring'],
          [<Code>flow-spring</Code>, 'Java', 'Spring Boot 自动配置：把你的 Bean 接进引擎、HTTP 接口、Flyway 建表、作业轮询、Micrometer 指标'],
          [<Code>flow-test-kit</Code>, 'Java（测试）', '契约核对：在你的项目里检查表、组织架构、扩展声明，并冒烟跑一单'],
          [<Code>@zerodep-dev/flow-ui</Code>, 'TS（Vue 3 + vue-tsx-pro）', '引擎客户端、表单运行时、待办与办理、流程与表单设计器、管理页面'],
        ]}
      />
      <Paragraph>
        依赖：持久层是 <Code>easy-query</Code>（我们维护的分支 <Code>org.dpdns.zerodep.easyquery:*:3.3.1-zd.1</Code>）；前后端之间的 JSON 是 <Code>json-wire</Code> 的 typed JSON；前端组件建在 <Code>vue-tsx-pro</Code> 组件库上。只支持 MySQL 8。
      </Paragraph>
    </Section>
    <Section id="request" title="一次办理怎么走">
      <OrderedList>
        <ListItem>办理页（WorkItemDetail）用 <Code>GET /flow/queries/work-items/{'{id}'}</Code> 取待办、实例、按节点权限过滤过的表单值、能选的处理结果</ListItem>
        <ListItem>用户点"同意"：前端校验表单，<Code>POST /flow/commands/complete</Code>，请求头带 <Code>Idempotency-Key</Code>（重复点、网络重试不会办两次）</ListItem>
        <ListItem>
          引擎在一个读已提交的事务里：锁住实例行 → 问 <Code>PermissionPolicy</Code> → 校验字段权限与表单 → 跑守卫、表单后端代码 → 按结果与条件选出边 → 到达下一个节点（解析处理人、建待办或作业）→ 写流转记录 → 事务内的监听
        </ListItem>
        <ListItem>提交后：发通知、调"提交后"的监听；可靠投递的进发件箱</ListItem>
        <ListItem>响应是 <Code>CommandResult</Code>：实例的新状态、新建与结束的待办、这次的流转记录，前端据此刷新，不用再查</ListItem>
      </OrderedList>
    </Section>
    <Section id="tables" title="数据">
      <Tbl
        head={['表', '存什么']}
        rows={[
          [<><Code>flow_process</Code>、<Code>flow_definition</Code></>, '流程本身（标识、名称、启停）与它发布的各个版本（JSON 快照，发布后不变）'],
          [<><Code>flow_form</Code>、 <Code>flow_form_version</Code></>, '表单与它的版本：源码、编译结果、字段清单、后端代码'],
          [<Code>flow_draft</Code>, '设计中的草稿（流程与表单共用）'],
          [<Code>flow_instance</Code>, '实例：状态、当前节点、发起人、标题与摘要、版本列'],
          [<Code>flow_form_data</Code>, '实例上表单的值（一行一个实例）'],
          [<><Code>flow_work_item</Code>、<Code>flow_candidate</Code></>, '待办与候选人（角色、部门在到达时展开）'],
          [<Code>flow_event</Code>, '流转记录：谁在什么时候做了什么、改了哪些字段'],
          [<Code>flow_cc</Code>, '抄送：每个收到的人一行，带已读'],
          [<Code>flow_job</Code>, '作业：定时、超时、提醒、异步动作、发件箱'],
          [<Code>flow_wait</Code>, '等待外部事件的节点（事件到了删掉这一行再推进）'],
          [<Code>flow_step</Code>, '最近一步的快照，用来"撤回这一步"'],
          [<Code>flow_request</Code>, '幂等记录：同一个 Idempotency-Key 再来时返回第一次的响应'],
          [<><Code>flow_calendar</Code>、<Code>flow_calendar_day</Code></>, '工作日历与节假日、调休'],
        ]}
      />
      <Paragraph>
        表由引擎的 Flyway 脚本建（单独的历史表 <Code>flow_schema_history</Code>，不碰你自己的迁移）。所有表可选带租户列；量大时可以把结束很久的实例归档到 <Code>*_archive</Code>。
      </Paragraph>
    </Section>
    <Section id="jobs" title="作业">
      <Paragraph>
        不在用户操作里当场做的事都是作业：定时节点到点、超时与提醒、无人认领、异步的自动处理、可靠投递。<Strong>作业轮询</Strong>（默认每 10 秒）取到期的作业执行；集群里每个实例都可以开，靠抢占不会重复做。失败按规则重试，用完进管理端的异常处理。
      </Paragraph>
    </Section>
    <Section id="versions" title="版本">
      <Paragraph>
        流程与表单都是"草稿 → 发布成不可变的版本"。实例记住发起时的流程版本，流程发布时把表单固定在当时的最新版本。所以改流程、改表单不会影响在走的单子；要影响，管理员显式迁移。
      </Paragraph>
    </Section>
  </DocPage>
));
