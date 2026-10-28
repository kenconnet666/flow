/** 流程管理与版本 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Shot } from '../../site/Shot';
import { Tbl } from '../../site/Tbl';

export const meta = {
  title: '流程管理与版本',
  description: '流程与表单的列表、停用与启用、导出导入与复制；每次发布是一个版本，可以载入旧版本、和已发布的对比。',
  order: 1,
  try: '/app/admin/processes',
};

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="list" title="流程列表">
      <Shot src="admin/01-processes.png" caption="流程管理：停用的也在列表里；每行最右边是操作" />
      <Tbl
        head={['操作', '说明']}
        rows={[
          ['编辑', '打开设计器，载入最新版本（有草稿时接着草稿改）'],
          ['导出 / 导入', '定义存成 JSON 文件，拿到另一套环境里导入（测试环境画好、生产环境导入）'],
          ['复制', '填新的标识和名称，复制出一个新流程'],
          ['停用 / 启用', '停用后发起页不再显示，已经在跑的单子照常走完'],
        ]}
      />
      <Paragraph>表单管理是同样的一张列表。停用的表单不能再被新发布的流程选用，已经用它的流程不受影响；被用过的表单只能停用，不能删除。</Paragraph>
    </Section>
    <Section id="versions" title="版本">
      <Paragraph>每次发布是一个新版本，发布后不能改。设计器顶上的 载入版本 可以打开以前的任意一版，在它的基础上改了再发布（等于回滚）。</Paragraph>
      <Shot src="admin/02-versions.png" caption="载入版本：每一版的发布说明、发布人与时间" />
      <Paragraph>改了以后点 与已发布版本对比，看这次改了什么再发布：对比的是现在的改动与载入的那一版。</Paragraph>
      <Shot src="admin/03-diff.png" caption="把财务审核改了名再对比：流程属性、节点、连线逐项列出加、删、改（只挪了位置不算）" />
      <Alert tone="info">新发起的单子用最新版本；在跑的单子留在它发起时的版本上，要换版本用流程详情里的 迁移版本。</Alert>
    </Section>
  </DocPage>
));
