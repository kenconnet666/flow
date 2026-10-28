/** 介绍：流程引擎是什么、能做什么、由哪几块组成、文档怎么读 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Code, Link, List, ListItem, Paragraph, Strong } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Shot } from '../../site/Shot';
import { Tbl } from '../../site/Tbl';

export const meta = {
  title: '介绍',
  description: '一个嵌进 Spring Boot 业务系统的审批流程引擎：管理员在浏览器里画流程、画表单，业务人员在待办里处理，程序员按需接入与扩展。',
  order: 1,
};

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="what" title="能做什么">
      <Paragraph>
        报销、采购、请假、合同这类"填一张单、按规则流转给不同的人审批"的事情，都可以用它来做。它不是一个独立的系统，而是一组嵌进你自己业务系统里的模块：
        后端是 Spring Boot 的依赖，前端是 Vue 3 的组件，数据存在你自己的 MySQL 里，用户、部门、角色用你自己的。
      </Paragraph>
      <Shot src="build/expense/04-condition.png" caption="在浏览器里画流程：选中连线，在右边写条件，金额大于等于 5000 才到总经理" />
      <Tbl
        head={['谁', '做什么', '在哪里看']}
        rows={[
          ['业务人员', '发起申请、在待办里同意或不同意、转办、委派、加签、留言、抄送、撤回', '日常使用'],
          ['管理员', '画流程、画表单、发布新版本；看所有单据，暂停、跳转、改派、迁移版本，处理异常', '搭建流程、表单、管理'],
          ['程序员', '把引擎接进业务系统，接入用户与权限；用扩展点加自己的规则、动作、节点', '开发'],
        ]}
      />
    </Section>
    <Section id="features" title="主要能力">
      <List>
        <ListItem>
          <Strong>流程</Strong>：人工审批、条件分支、并行、包容分支、定时、自动处理、等外部事件、子流程、抄送；会签（全部通过、一人通过、依次、按比例、按票数）；
          退回到走过的任意一步、上一步的人选下一步处理人、自动跳过、超时提醒与自动处理
        </ListItem>
        <ListItem>
          <Strong>协作</Strong>：认领、转办、委派、前后加签、催办、留言并 @ 人、抄送、发起人撤回、处理人撤回刚处理的一步、批量认领与同意、草稿与暂存
        </ListItem>
        <ListItem>
          <Strong>表单</Strong>：拖着画，也能写代码；明细表、附件、人员、显示与必填条件；每个节点能看、能改哪些字段单独配；需要时写后端代码查预算、写业务表
        </ListItem>
        <ListItem>
          <Strong>管理</Strong>：版本并存（改了流程不影响在走的单子）、流程监控、异常处理、工作日历、统计；发布前校验，不会把有问题的流程发出去
        </ListItem>
        <ListItem>
          <Strong>接入</Strong>：Spring Boot 自动配置；组织架构、权限、通知、动作都是接口，按需实现；强类型 JSON，金额、日期、长编号来回不丢
        </ListItem>
      </List>
    </Section>
    <Section id="parts" title="由哪几块组成">
      <Tbl
        head={['模块', '是什么']}
        rows={[
          [<Code>flow-model</Code>, '流程定义、表单字段清单与它们的校验规则；前后端各一份（Java 与 TypeScript），规则一致'],
          [<Code>flow-core</Code>, '引擎本体：流转、待办、协作、作业、查询；接入方要实现的接口都在它的 spi 包'],
          [<Code>flow-spring</Code>, 'Spring Boot 适配：自动配置、HTTP 接口（/flow 开头）、作业轮询、监控指标'],
          [<Code>flow-test-kit</Code>, '契约核对：上线前、升级后在你的项目里跑一遍，确认组织架构、扩展都接对了'],
          [<Code>@zerodep-dev/flow-ui</Code>, '前端：待办、办理、发起、详情、流程设计器、表单设计器、管理页面，建在 vue-tsx-pro 组件库上'],
        ]}
      />
    </Section>
    <Section id="read" title="怎么读这份文档">
      <Paragraph>
        第一次接触，先看 <Link href="/start/quick-start">快速体验</Link> 把示例跑起来，再照 <Link href="/build/expense">教程 1</Link> 画一个流程。
        "搭建流程"的六个教程由浅入深，每个都从新建流程画到发布，再用真实的账号跑一单；配图都是在示例里一步步操作截下来的，点图可以看大图。
      </Paragraph>
      <Alert tone="info">示例里有几个现成的人：林小满（发起人）、周启航与沈若水（财务）、总经理、许知远。右上角可以切换当前的人。</Alert>
    </Section>
  </DocPage>
));
