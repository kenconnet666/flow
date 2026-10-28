/** 接口与 Java API */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Code, CodeBlock, Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Tbl } from '../../site/Tbl';

export const meta = {
  title: '接口与 Java API',
  description: '前端组件调的是引擎自带的 HTTP 接口（/flow/commands/*、/flow/queries/*）；后端自己的代码直接调 FlowEngine。两边的命令一一对应，错误都是业务错误码。',
  order: 10,
};

const envelope = `// 成功
{ "code": "ok", "data": { ... } }
// 失败：HTTP 照样是 200
{ "code": "flow.work-item-not-open", "message": "待办已处理", "params": { "workItem": "123" } }
// 校验类的失败带明细
{ "code": "flow.validation-failed", "message": "表单校验不通过", "errors": [{ "field": "amount", "code": "max" }] }`;

const java = `@Service
public class PurchaseService {
    private final FlowEngine<AppUser> engine;

    public PurchaseService(FlowEngine<AppUser> engine) { this.engine = engine; }

    /** 从自己的业务单据发起：businessKey 是单号，外部事件可以按它找到实例 */
    public Long submit(AppUser user, Purchase p) {
        CommandResult result = engine.start(user, new StartCommand("purchase",
            FormValues.of(Map.of("title", p.getTitle(), "amount", p.getAmount())), null, p.getNo(), null, 0, null));
        return result.getInstance().getId();
    }

    /** 我的待办（第 1 页，每页 20 条） */
    public Page<TodoItem> todo(AppUser user) { return engine.todo(user, 1, 20); }
}`;

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="http" title="HTTP 约定">
      <Paragraph>
        <Code>flow.http.enabled: true</Code> 时有这些接口，前缀默认 <Code>/flow</Code>。一个命令一个 POST，一个查询一个 GET（列表收分页参数）；响应是 typed JSON；HTTP 一律 200，成功失败看 <Code>code</Code>。命令的请求头可以带 <Code>Idempotency-Key</Code>：同一个键再来时直接返回第一次的结果，重复点、网络重试不会办两次。
      </Paragraph>
      <CodeBlock code={envelope} lang="jsonc" />
    </Section>
    <Section id="commands" title="命令">
      <Tbl
        head={['路径（POST /flow/…）', '请求体']}
        rows={[
          ['commands/start', '{ key, form, variables, businessKey, title, priority, picks }'],
          ['commands/claim', '{ workItemId }'],
          ['commands/complete', '{ workItemId, outcome, comment, form, picks, returnTarget }'],
          ['commands/withdraw', '{ instanceId, reason }（发起人撤回整单）'],
          ['commands/transfer、delegate', '{ workItemId, userId, reason }'],
          ['commands/add-signers、remove-signers', '{ workItemId, users, mode: before | after | parallel, reason }'],
          ['commands/release', '{ workItemId, reason }（退回收件箱）'],
          ['commands/remind、comment', '{ instanceId, workItemId, message }、{ instanceId, text }'],
          ['commands/signal', '{ instanceId 或 businessKey, event, outcome, data }（外部事件）'],
          ['commands/batch-claim、batch-complete', '{ workItemIds }、{ workItemIds, outcome, comment }（各自成败）'],
          ['commands/withdraw-step', '{ workItemId }（撤回刚处理的一步）'],
          ['commands/cc、assign、request-reassign', '抄送、负责人指定承办人、申请改派'],
          ['commands/save-draft、save-start-draft', '暂存待办、发起前存草稿'],
          ['admin/terminate、suspend、resume', '{ instanceId, reason }'],
          ['admin/migrate、change-data', '迁移版本、改数据（都要写 reason）'],
          ['admin/jump、reassign、skip-job、retry-job', '{ instanceId, node, reason }、{ workItemId, userId, reason }、{ jobId, outcome, reason }、{ jobId }'],
          ['definitions/publish?note=', '定义的 JSON 本身'],
          ['forms/publish', '{ key, name, category, manifest, clientSource, serverLanguage, serverSource, note }'],
        ]}
      />
      <Paragraph>命令的 data 是 <Code>{'{ instance, workItems, closed, events }'}</Code>：实例的新状态、新建的待办、结束的待办、这次的流转记录。</Paragraph>
    </Section>
    <Section id="queries" title="查询">
      <Tbl
        head={['路径（GET /flow/…）', '返回']}
        rows={[
          ['queries/startable', '我能发起的流程'],
          ['queries/start/{key}', '发起页：流程与开始节点上的表单'],
          ['queries/todo、done、started、cc', '我的待办、我已办、我发起的、抄送我的（分页、筛选、排序）'],
          ['queries/work-items/{id}', '待办详情：待办、实例、按权限过滤的表单、能选的处理结果'],
          ['queries/instances/{id}', '实例详情：表单、流程图上的进度、流转记录'],
          ['queries/instances/{id}/progress', '给外部参与方看的进度（内容有限）'],
          ['queries/designations、start-drafts', '待我指定、我的发起草稿'],
          ['queries/stats/processes、nodes、handlers', '统计'],
          ['queries/instances?status=&processKey=&keyword=', '管理端搜实例'],
          ['queries/incidents', '异常（重试用完的作业）'],
          ['queries/processes、forms', '管理端的流程、表单列表'],
          ['queries/processes/{key}/versions、definition?version=', '版本列表、某个版本的 JSON'],
          ['queries/extensions', '扩展目录（设计器用）'],
        ]}
      />
    </Section>
    <Section id="errors" title="错误码">
      <Tbl
        head={['码', '什么时候']}
        rows={[
          ['flow.not-found', '实例、待办、定义不存在'],
          ['flow.forbidden', 'PermissionPolicy 不放行，或 ViewPolicy 不让看（reason: not-visible）'],
          ['flow.not-candidate', '不是这个待办的处理人或候选'],
          ['flow.already-claimed', '别人已认领'],
          ['flow.work-item-not-open', '待办已处理、已取消（重复提交）'],
          ['flow.instance-not-active', '实例已结束、已暂停'],
          ['flow.concurrent-modification', '同时有人改了（乐观锁），刷新后再试'],
          ['flow.guard-rejected', '提交前检查不通过，带原因'],
          ['flow.validation-failed', '表单或参数校验不通过，errors 里按字段'],
          ['flow.definition-invalid', '发布时校验不通过，带问题列表'],
          ['flow.extension-failed', '扩展（解析器、动作、表单后端代码）抛错，带原始错误'],
          ['flow.internal-error', '没认出来的异常'],
        ]}
      />
      <Paragraph>Java 里是 <Code>FlowException</Code>（带码与参数），前端的请求客户端按码从语言包取文案。</Paragraph>
    </Section>
    <Section id="java" title="在后端代码里调">
      <Paragraph>
        <Code>{'FlowEngine<你的用户类型>'}</Code> 是 Spring Bean，每个命令、查询都是它的一个方法，第一个参数是操作人（同样经过 PermissionPolicy）。在你自己的事务里调时注意隔离级别（见 后端接入）。
      </Paragraph>
      <CodeBlock code={java} lang="java" />
      <Paragraph>不用 Spring 时 <Code>FlowEngine.builder(easyQuery)</Code> 建一个，组织架构、扩展点在 builder 上给；作业由你定时调 <Code>engine.runDueJobs()</Code>。</Paragraph>
    </Section>
  </DocPage>
));
