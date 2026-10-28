/** 表单设计器：画一张报销单 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Alert, Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Ops } from '../../site/Ops';
import { Shot } from '../../site/Shot';
import { Tbl } from '../../site/Tbl';

export const meta = {
  title: '表单设计器',
  description: '画一张报销单：金额、费用类别、只在差旅时出现的同行人、发票、费用明细。表单就是一份 TSX 代码，拖拽设计、手写代码、写字段清单三种写法改的是同一份。',
  order: 1,
};

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="ways" title="三种写法">
      <Tbl
        head={['写法', '适合']}
        rows={[
          ['字段清单（自动排版）', '只列字段（JSON），表单按顺序自动排成一列；最快'],
          ['设计', '拖组件、改属性；布局（分组、栅格、标签页）、提示文字都能摆'],
          ['代码', '表单的 TSX 源码：设计里做不到的联动、计算、自定义组件在这里写；设计与代码是同一份，两边随时切换'],
        ]}
      />
      <Paragraph>另有 后端代码 页签：表单需要在服务端校验、算字段、写业务表时，在这里写一个 Java 类（见 开发 → 表单后端代码）。</Paragraph>
    </Section>
    <Section id="new" title="1. 新建">
      <Ops items={['以 总经理 身份进 表单管理，点 新建表单', '标识填 expense-form，标题填 报销单', '切到 设计']} />
      <Shot src="forms/01-list.png" caption="表单管理：所有表单与它们的最新版本" />
      <Shot src="forms/02-designer.png" caption="设计：左边是组件（布局、展示、字段），中间画布，右边属性" />
    </Section>
    <Section id="fields" title="2. 加字段">
      <Ops
        items={[
          '点左边 字段 → 金额：画布上多了一个字段，右边是它的属性',
          '字段名填 amount（条件、权限、接口里都用它，小写字母开头，发布后别改），标签填 金额，打开 必填',
        ]}
      />
      <Shot src="forms/03-field.png" caption="字段属性：字段名、标签、说明、必填、范围……" />
      <Tbl
        head={['组件', '字段类型', '值']}
        rows={[
          ['文字 / 多行文字', 'text', '字符串；可以限长度、正则'],
          ['数字', 'number', '数；可以限范围、只要整数'],
          ['金额', 'decimal', '精确小数（不会有 0.1 + 0.2 的误差），可设小数位'],
          ['是否', 'boolean', '开关'],
          ['日期 / 时间', 'date / datetime', '日期；时刻（按 UTC 存）'],
          ['选项', 'select', '选项的值，可多选'],
          ['人员', 'people', '人、部门、角色（带名字）；可以当后面节点的处理人'],
          ['附件', 'file', '文件列表，文件本身由接入方存'],
          ['明细表', 'list', '多行，每行若干列（明细里不能再套明细）'],
        ]}
      />
    </Section>
    <Section id="options" title="3. 选项">
      <Ops items={['加 选项：字段名 category，标签 费用类别，选项一行一个：差旅、办公、招待；必填']} />
      <Shot src="forms/04-options.png" caption="选项一行一个；值和显示文字相同，要不同时在代码里写" />
    </Section>
    <Section id="show-when" title="4. 显示条件">
      <Ops
        items={[
          '加 人员：字段名 companions，标签 同行人',
          '显示条件：添加条件 → 字段 费用类别，等于，差旅',
        ]}
      />
      <Shot src="forms/05-show-when.png" caption="只有费用类别是差旅时才出现同行人" />
      <Alert tone="info">显示条件不成立时字段隐藏，值也会被丢掉、不参与校验——前后端按同一套规则算。必填条件（requiredWhen）同样可以配。</Alert>
    </Section>
    <Section id="list" title="5. 明细表">
      <Ops
        items={[
          '加 附件：字段名 invoice，标签 发票',
          '加 明细表：字段名 items，标签 费用明细',
          '列 1：字段名 item，标签 项目；加一列，列 2：字段名 money，控件 金额，标签 金额',
          '最后加 多行文字：字段名 note，标签 财务意见（发起人不填，财务审核时填——下一篇配权限）',
        ]}
      />
      <Shot src="forms/06-list.png" caption="明细表的列：每列有自己的字段名、控件、标签" />
    </Section>
    <Section id="preview" title="6. 预览与代码">
      <Paragraph>点 预览 直接试填：选了差旅，同行人出现；明细表可以加行。只读预览看审批人看到的样子；"按节点看"套用某个流程节点上的字段权限。</Paragraph>
      <Shot src="forms/07-preview.png" caption="预览：表单在这个页面里真实运行" />
      <Shot src="forms/08-code.png" caption="代码：设计出来的就是这份 TSX——上面是字段清单 defineFields，下面是布局" />
      <Shot src="forms/09-backend.png" caption="后端代码页签：可选，Java 写的 FormHandler，能试编译" />
    </Section>
    <Section id="publish" title="7. 发布">
      <Ops items={['回到 表单 页签，发布说明填 首次发布，点 发布：这是第 1 版']} />
      <Paragraph>表单的修改同样自动存草稿；发布后每个版本都留着，不会被改。下一篇把它换给教程 1 的报销流程。</Paragraph>
    </Section>
  </DocPage>
));
