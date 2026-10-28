/** 组织与权限 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Code, CodeBlock, Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Tbl } from '../../site/Tbl';

export const meta = {
  title: '组织与权限',
  description: '引擎用你的用户、部门、角色：OrgDirectory 告诉它谁是谁，PermissionPolicy 决定能做什么，ViewPolicy 决定能看哪一单。',
  order: 5,
};

const policy = `@Component
public class AppPermissions implements PermissionPolicy<AppUser> {
    private static final Set<String> ADMIN_ONLY = Set.of(
        "terminate", "suspend", "resume", "jump", "reassign", "migrate", "changeData", "retryJob", "skipJob",
        "instances.search", "incidents", "stats");

    @Override
    public boolean allows(AppUser actor, String operation, String target) {
        boolean design = operation.startsWith("definitions.") || operation.startsWith("forms.") && !operation.equals("forms.action")
            || operation.startsWith("drafts.") || operation.startsWith("calendars.");
        if (design || ADMIN_ONLY.contains(operation)) return actor.getRoles().contains(ADMIN_ROLE);
        return true;
    }
}`;

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="model" title="组织模型">
      <Paragraph>
        标准的组织模型是<strong>用户、部门、角色</strong>，编号是你组织表的主键。用户实现 <Code>Actor</Code>（编号、名字、部门、角色），别的归属（岗位、项目组）放进 <Code>getGroups()</Code>，在流程里写成"扩展的种类"。
      </Paragraph>
    </Section>
    <Section id="directory" title="OrgDirectory">
      <Tbl
        head={['方法', '必须', '用在']}
        rows={[
          [<Code>userName(id)</Code>, '是', '处理人、抄送人的名字快照'],
          [<Code>members(kind, id)</Code>, '用到会签、抄送写角色或部门时', '把角色、部门展开成人'],
          [<Code>administrators()</Code>, '建议', '处理人解析为空时转给谁；流程管理员也总能看实例'],
          [<Code>inboxManager(kind, id)</Code>, '可选', '收件箱的负责人：久无人认领时由谁指定承办人'],
          [<Code>standIn(userId, processKey, at)</Code>, '可选', '这个人不在岗（休假、出差）时新待办交给谁；接你的休假或代理设置'],
        ]}
      />
      <Alert tone="info">
        候选人在节点到达时解析一次，写进待办的候选表；之后组织架构变了，已生成的待办不跟着变（管理员改派或负责人指定）。角色收件箱认领时按操作人当时的角色判断，所以换了角色马上生效。
      </Alert>
    </Section>
    <Section id="permission" title="PermissionPolicy：能做什么">
      <Paragraph>
        每个命令、查询之前问一次 <Code>allows(操作人, 操作名, 目标)</Code>，默认全部放行——页面与接口的登录、保护由你自己的框架负责。通常至少把设计与管理员操作收紧到管理员：
      </Paragraph>
      <CodeBlock code={policy} lang="java" />
      <Tbl
        head={['类别', '操作名']}
        rows={[
          ['发起与处理', 'start、saveStartDraft、discardStartDraft、startDrafts、startable、startView、claim、complete、saveDraft、withdraw、withdrawStep、forms.action'],
          ['查询', 'todo、done、started、cc.list、workItem、instance、progress、designations、instances.search、processes.list、incidents、stats'],
          ['协作', 'transfer、delegate、addSigners、removeSigners、release、remind、comment、cc、readCc、readWorkItem、signal、assign、requestReassign'],
          ['设计', 'definitions.*、forms.publish / list / versions / read / status / compile、drafts.*、extensions.read、calendars.*'],
          ['管理员', 'terminate、suspend、resume、jump、reassign、retryJob、skipJob、migrate、changeData'],
        ]}
      />
      <Paragraph>目标是流程标识、待办编号或实例编号，可以按流程细分（某个部门的管理员只管自己的流程）。</Paragraph>
    </Section>
    <Section id="view" title="ViewPolicy：能看哪一单">
      <Paragraph>
        打开实例详情、待办详情、调表单的 actions、评论之前问它。默认：参与过的人（发起人、流程管理员、处理过或正在处理、候选、被抄送、流转记录里有）总能看，别人要有 <Code>instances.search</Code> 的权限——所以收紧了 instances.search，没参与过的人自然打不开。要"同部门能看""按密级"时替换它。
      </Paragraph>
    </Section>
    <Section id="engine-rules" title="引擎自己的规则">
      <Paragraph>只有候选人能认领、只有发起人能撤回、只有处理人能处理、只读与隐藏的字段改不了——这些不经过上面两个策略，一直生效。</Paragraph>
    </Section>
  </DocPage>
));
