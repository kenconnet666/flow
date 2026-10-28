/** 表单与流程：绑定、字段权限、表单出新版本 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Ops } from '../../site/Ops';
import { Shot } from '../../site/Shot';
import { Tbl } from '../../site/Tbl';

export const meta = {
  title: '表单与流程',
  description: '一个流程用一张表单，每个人工节点决定每个字段能不能看、能不能改。把报销单换给报销流程，让财务意见只在财务审核时可填；表单改版后让流程跟上。',
  order: 2,
  try: '/app/admin/processes/expense',
};

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="rule" title="一个流程一张表单">
      <Paragraph>
        同一个流程的各个节点看的是同一份业务数据，只是能看、能改的不同。某一步要多填的东西（财务填意见、付款日期）就是这张表单里的字段，在别的节点上隐藏或只读。审批意见、附件不进表单，它们由办理页的意见框收、记在流转记录里。
      </Paragraph>
    </Section>
    <Section id="bind" title="1. 换表单">
      <Ops
        items={[
          '流程管理 → 报销，打开设计器',
          '什么都不选，右边流程属性的 表单 改成 报销单',
        ]}
      />
      <Shot src="forms/10-bind.png" caption="流程属性里选表单；条件、实例标题里的字段随之换成报销单的字段" />
      <Alert tone="warning">换了表单，原来条件里用的字段要还在：教程 1 的条件用的是 金额（amount），报销单也有 amount，所以不用改。字段名不在了，校验会指出来。</Alert>
    </Section>
    <Section id="access" title="2. 字段权限">
      <Ops
        items={[
          '点 提交 节点，在 表单字段 里把 财务意见 设成 隐藏——发起人看不到这一项',
          '点 财务审核 节点，把 财务意见 设成 可编辑',
          '发布说明填 换成报销单，发布',
        ]}
      />
      <Shot src="forms/11-access.png" caption="每个节点的表单字段：没列出的字段按“同默认”" />
      <Tbl
        head={['权限', '页面上', '服务端']}
        rows={[
          ['同默认', '发起节点能改，其他节点只读', '—'],
          ['隐藏', '不显示', '值根本不发给浏览器；提交时忽略'],
          ['只读', '显示，不能改', '提交时忽略，用存着的值'],
          ['可编辑', '能改，必填按表单的设置', '按表单校验'],
          ['必填', '能改，且一定要填', '同上，另外一定要有值'],
        ]}
      />
      <Paragraph>权限由服务端把关：即使有人绕过页面直接调接口，只读、隐藏的字段也改不了。每次提交改了哪些字段（旧值、新值）记在流转记录里。</Paragraph>
    </Section>
    <Section id="version" title="3. 表单出新版本">
      <Paragraph>流程发布时把表单固定在当时的最新版本。表单再发新版本，已发布的流程和在跑的单子都不受影响——要用新表单，流程得重新发布一次。</Paragraph>
      <Ops
        items={[
          '表单管理 → 报销单，设计里加一个 日期：字段名 spentOn，标签 发生日期，发布',
          '发布后编辑器顶上提示哪些流程还在用旧版本，点 重新发布这 1 个流程',
          '林小满再发起报销，就有发生日期了',
        ]}
      />
      <Shot src="forms/12-outdated.png" caption="一键让还在用旧表单的流程各发一个新版本（只影响之后发起的）" />
      <Shot src="forms/13-start.png" caption="发起报销：报销单第 2 版，选了差旅出现同行人" />
      <Alert tone="info">在跑的单子要换到新版本，管理员在流程详情里 迁移版本。</Alert>
    </Section>
  </DocPage>
));
