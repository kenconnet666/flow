/** 流程监控与管理员操作 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Shot } from '../../site/Shot';
import { Tbl } from '../../site/Tbl';

export const meta = {
  title: '流程监控与管理员操作',
  description: '查所有单子；打开一单可以暂停、跳转、改派、改数据、迁移版本、终止。每个操作都要写说明，记进流转记录。',
  order: 2,
  try: '/app/admin/monitor',
};

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="monitor" title="流程监控">
      <Paragraph>列出所有单子，按状态、流程、标题筛，新的在前。点标题打开流程详情，详情里多了一块 管理员操作。</Paragraph>
      <Shot src="admin/04-monitor.png" caption="流程监控" />
      <Shot src="admin/05-actions.png" caption="管理员操作：整单的操作在上面，每个打开着的待办后面有改派" />
    </Section>
    <Section id="actions" title="能做什么">
      <Tbl
        head={['操作', '什么时候用', '效果']}
        rows={[
          ['暂停 / 恢复', '要先查清楚再继续', '暂停后所有待办都不能办，恢复后接着走'],
          ['跳转', '流程卡住了，或要重走某一步', '取消当前的待办，从选定的节点重新开始'],
          ['改派', '处理人离职、请假', '把一个待办换给别人'],
          ['改数据', '表单填错了，流程已经过了那一步', '只写要改的字段，改前改后都记下'],
          ['迁移版本', '流程改版后，让在跑的单子也用新版本', '表单数据、流转记录都留着；正停着的节点新版本里没有时要指定换成哪个'],
          ['终止', '单子作废', '打开着的待办都取消，单子结束（已终止）'],
        ]}
      />
      <Shot src="admin/06-jump.png" caption="跳转：选节点、写说明" />
      <Shot src="admin/07-reassign.png" caption="改派：选人、写说明" />
      <Shot src="admin/08-change-data.png" caption="改数据：值写 null 是清空" />
      <Shot src="admin/09-migrate.png" caption="迁移版本" />
      <Shot src="admin/10-suspended.png" caption="暂停后状态是已暂停，按钮换成恢复" />
      <Alert tone="info">谁能看到管理端、能做哪些操作，由接入方的权限策略（PermissionPolicy）决定，见 开发 → 组织与权限。</Alert>
    </Section>
  </DocPage>
));
