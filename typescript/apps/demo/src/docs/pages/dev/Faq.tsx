/** 上线核对与常见问题 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Code, List, ListItem } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Tbl } from '../../site/Tbl';

export const meta = {
  title: '上线核对与常见问题',
  description: '上线前逐项核对；遇到报错先在这里查。',
  order: 12,
};

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="checklist" title="上线前核对">
      <List>
        <ListItem>flow-test-kit 的契约核对全部通过</ListItem>
        <ListItem>数据库是 MySQL 8，账号有建表权限（或表由你的迁移工具建好）；事务隔离是读已提交，或只靠引擎自己开事务</ListItem>
        <ListItem>OrgDirectory 的 administrators()、members() 接好了（会签、抄送按角色或部门时要）</ListItem>
        <ListItem>当前操作人从登录信息取，不是示例里的请求头</ListItem>
        <ListItem>PermissionPolicy 把设计、管理员操作、instances.search 收紧了（ViewPolicy 跟着生效）</ListItem>
        <ListItem>不想让"能发布表单"等于"能在服务器上跑 Java"时，设 <Code>flow.forms.server-code: false</Code></ListItem>
        <ListItem>接口响应是 typed JSON：看一个带金额的接口，响应里有 $types</ListItem>
        <ListItem>集群部署时每个实例都可以开作业轮询；不开就关掉并接调度平台</ListItem>
        <ListItem>量大时打开归档（flow.archive.enabled）</ListItem>
        <ListItem>监控上看 flow.jobs.failed 与 flow.jobs.due.oldest</ListItem>
      </List>
    </Section>
    <Section id="faq" title="常见问题">
      <Tbl
        head={['现象', '原因与处理']}
        rows={[
          ['命令报 flow commands need a READ COMMITTED transaction', '在你的可重复读事务里调了命令：方法上写 isolation = READ_COMMITTED，或把默认隔离级别设成读已提交'],
          ['打开实例报 flow.forbidden、reason: not-visible', 'ViewPolicy 不让看：不是参与者，也没有 instances.search 的权限'],
          ['列表排序报 flow.validation-failed（sort: unknown-column）', '排序列不在白名单里'],
          ['金额在前端是字符串、日期不对；控制台提示 /flow 的响应不是 typed JSON', 'WireResponseAdvice 没配或 basePackages 漏了引擎的包；或前端没给 wire 选项'],
          ['处理人为空，单子到了管理员那里', '处理人解析为空时默认转流程管理员（settings.emptyAssignee）；查解析器、角色成员'],
          ['定时、超时、自动处理不动', '作业轮询没开或停了：看 flow.jobs.due.oldest；关了轮询时要自己定时调 engine.runDueJobs()'],
          ['自动处理失败后流程停住', '重试用完进了异常处理：管理端重试、跳过或终止'],
          ['发布流程报引用了不存在的字段', '换了表单或改了字段名：条件、实例标题、字段权限里还用着旧名字'],
        ]}
      />
    </Section>
  </DocPage>
));
