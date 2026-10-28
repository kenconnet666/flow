/** 流程定义参考 */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Code, CodeBlock, Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Tbl } from '../../site/Tbl';

export const meta = {
  title: '流程定义参考',
  description: '设计器画的就是一份 JSON。面板里没有的设置（提醒、超时、无人认领、流程变量、发起人范围、通知）在设计器的 JSON 页签里写，这里是字段的参考。',
  order: 9,
};

const top = `{
  "key": "purchase",
  "name": "采购申请",
  "category": "采购",
  "form": { "key": "purchase-form" },             // 一个流程一张表单；发布时固定到当时的最新版本
  "title": "采购申请：{form.title}",                // 实例标题的模板
  "summary": ["form.title", "form.amount"],         // 待办列表的摘要列
  "variables": [{ "name": "round", "type": "number", "label": "协商轮次", "default": 0 }],
  "starters": [{ "department": 10 }],               // 谁能发起；不写时人人可发起
  "settings": { "withdraw": "beforeAction", "emptyAssignee": "admin", "calendar": "default" },
  "nodes": [ ... ],
  "edges": [ ... ],
  "layout": { ... }                                 // 节点位置，引擎不读
}`;

const task = `{
  "id": "review", "type": "task", "name": "财务复核",
  "assignees": [{ "role": 1 }],
  "outcomes": [
    { "key": "pass", "label": "通过" },
    { "key": "back", "label": "退回修改", "kind": "return", "target": "choose", "comment": "required" },
    { "key": "reject", "label": "否决", "kind": "terminate", "comment": "required", "confirm": true }
  ],
  "multi": { "participants": [{ "role": 1 }], "complete": "all" },
  "due": { "workdays": 2 },
  "reminders": [{ "before": { "hours": 4 } }, { "every": { "workdays": 1 }, "times": 3 }],
  "overdue": { "action": "mark" },
  "unclaimed": { "after": { "workdays": 3 }, "action": "assignByManager" },
  "allow": { "delegate": false },
  "form": { "default": "readonly", "fields": { "note": "editable" } },
  "notify": { "arrived": { "template": "purchase-arrived" } }
}`;

const edge = `{ "id": "large", "from": "route", "to": "approve",
  "when": { "all": [{ "field": "form.amount", "op": "gte", "value": "10000" }] },
  "label": "≥ 一万" }

{ "id": "other", "from": "route", "to": "done", "default": true }

{ "id": "again", "from": "negotiate", "to": "review",
  "effects": [{ "increment": "round" }],
  "when": { "field": "round", "op": "lt", "value": 3 } }`;

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="top" title="顶层">
      <CodeBlock code={top} lang="jsonc" />
      <Tbl
        head={['settings', '说明', '默认']}
        rows={[
          [<Code>withdraw</Code>, '发起人撤回：beforeClaim（第一个待办被认领前）、beforeAction（有人处理前）、never', 'beforeAction'],
          [<Code>emptyAssignee</Code>, '处理人解析为空：autoPass、admin（转流程管理员）、error', 'admin'],
          [<Code>autoSkip</Code>, '{ "initiator": true, "sameAsPrevious": true }：处理人是发起人、和上一步同一人时自动通过', '都关'],
          [<Code>formView</Code>, '不在待办里看表单的人（发起人、抄送、管理）对哪些字段隐藏', '全部只读'],
          [<Code>calendar</Code>, '用哪份工作日历', 'default'],
          [<Code>notify</Code>, '按事件换模板、加收件人、不发', '全发'],
        ]}
      />
    </Section>
    <Section id="refs" title="取值与引用">
      <Tbl
        head={['写法', '指的是']}
        rows={[
          [<Code>form.字段名</Code>, '表单的值，如 form.amount'],
          [<Code>sys.名字</Code>, '引擎给的只读值：sys.initiator（发起人）、sys.startedAt、sys.round.节点（第几轮）'],
          ['变量名', '流程变量（variables 里声明的，名字不带点）'],
          [<Code>fn.名字</Code>, '接入方注册的计算值，只能用在连线条件里'],
        ]}
      />
      <Paragraph>值可以直接写，也可以写成 <Code>{'{ "ref": "form.buyer" }'}</Code> 取别处的值。</Paragraph>
    </Section>
    <Section id="assignees" title="处理人来源">
      <Tbl
        head={['来源', '写法']}
        rows={[
          ['角色收件箱', '{ "role": 1 }'],
          ['指定的人', '{ "user": 1001 }，或 { "user": { "ref": "form.buyer" } }'],
          ['部门', '{ "department": 20 }'],
          ['发起人', '{ "initiator": true }'],
          ['同某节点', '{ "sameAs": "check" }：那个节点最后的处理人'],
          ['解析器', '{ "resolver": "app.leader", "args": { "level": 2 } }'],
          ['扩展的种类', '{ "group": "position", "id": 7 }（Actor.getGroups 给出归属）'],
          ['上一步选', '{ "pick": { "from": [来源…], "max": 1 } }'],
        ]}
      />
      <Paragraph>几个来源取并集。解析出一个人直接落到他；多个人或含角色、部门时进收件箱，候选人认领。要"每人各办一份"用会签 multi。</Paragraph>
    </Section>
    <Section id="task" title="人工节点">
      <CodeBlock code={task} lang="jsonc" />
      <Tbl
        head={['字段', '说明']}
        rows={[
          [<Code>outcomes[].kind</Code>, 'approve（默认）、reject、return、escalate、terminate；terminate 不要出边，return 写 target: "choose" 时处理人当场选退回到哪'],
          [<Code>multi.complete</Code>, 'all、any、sequential、ratio（加 ratio）、votes（加 votes，参与人可带 weight）；ratio、votes 要写 fallback'],
          [<Code>due</Code>, '时限：{ workdays }、{ days }、{ hours }、{ minutes }，或 { until: { ref } }'],
          [<Code>overdue</Code>, '超时：mark（只标记，默认）、complete（按某个结果自动完成）、escalate（走提级出边）'],
          [<Code>unclaimed</Code>, '收件箱久无人认领：assignByManager（转负责人指定）或 remind'],
          [<Code>returnTo</Code>, '被退回的节点重新提交后：restart（从目标往下照常走，默认）、resubmit（直接回到退回它的节点）'],
          [<Code>allow</Code>, 'transfer、delegate、addSign、withdraw、remind，默认全开'],
          [<Code>form</Code>, '这一步的字段权限：default 加 fields（hidden、readonly、editable、required）'],
        ]}
      />
    </Section>
    <Section id="nodes" title="其他节点">
      <Tbl
        head={['type', '自己的字段']}
        rows={[
          ['start / end', 'end 的 result（approved、rejected……），terminate: true 时到这里取消别的分支'],
          ['exclusive / parallel / inclusive', '—（分叉与汇合成对）'],
          ['service', 'action、args、mode（async 默认 / sync）、retry（{ times, backoff }）、onFailure（manual 默认 / error）'],
          ['call', 'process、mode（sync / async）、input、output、forEach + as（对列表每一项各发起一个）'],
          ['timer', 'wait：时长，或 { until: { ref } }'],
          ['receive', 'event、outcomes、due、overdue、output（事件带的数据写到哪）'],
          ['cc', 'to（来源同处理人）、message'],
          ['x-名字', '接入方注册的自定义节点，其他属性原样交给它'],
        ]}
      />
      <Paragraph>所有节点都可以带 lane（泳道）、description、meta（业务信息，引擎不解释）。</Paragraph>
    </Section>
    <Section id="edges" title="连线">
      <CodeBlock code={edge} lang="jsonc" />
      <Paragraph>
        <Code>outcome</Code> 选哪个结果走这条；<Code>when</Code> 是条件树（field、op、value，加 all / any / not，比较方式同组件库的条件构造器）；<Code>default</Code> 是网关的默认出边；<Code>kind</Code> 为 return、escalate 时决定画法与目标怎么分配处理人；<Code>effects</Code> 走过时改流程变量（set、increment）。选边时先取写明这个结果的，再取没写结果的，都没有走默认。
      </Paragraph>
    </Section>
  </DocPage>
));
