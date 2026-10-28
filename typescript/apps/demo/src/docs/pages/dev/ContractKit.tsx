/** 契约核对（flow-test-kit） */
import { _component } from '@zerodep-dev/vue-tsx-pro-tsx';
import { Code, CodeBlock, Paragraph } from '@zerodep-dev/vue-tsx-pro-ui';
import { DocPage, Section } from '../../site/DocPage';
import { Tbl } from '../../site/Tbl';

export const meta = {
  title: '契约核对',
  description: '接好以后在自己的项目里跑一遍 flow-test-kit：表建对了没有、组织架构查得到人没有、扩展声明写对了没有，最后用你真实的账号走一单。',
  order: 11,
};

const test = `@SpringBootTest
class FlowContractIT extends FlowContractTest<AppUser> {
    @Autowired FlowEngine<AppUser> engine;
    @Autowired EasyEntityQuery easyQuery;      // 引擎用的那一个
    @Autowired OrgDirectory org;

    @Override
    protected FlowContract<AppUser> contract() {
        return FlowContract.<AppUser>builder().engine(engine).db(easyQuery).org(org)
            .admin(users.byName("admin"))          // 流程管理员
            .starter(users.byName("lin"))          // 能发起的人
            .role(FINANCE_ROLE)                    // 某个角色的编号
            .member(users.byName("zhou"))          // 这个角色的一个成员
            .build();
    }
}`;

export default _component(() => () => (
  <DocPage meta={meta}>
    <Section id="how" title="怎么用">
      <Paragraph>测试依赖加 <Code>flow-test-kit</Code>，继承 <Code>FlowContractTest</Code>，连测试库，给几个真实的账号：</Paragraph>
      <CodeBlock code={test} lang="java" />
      <Paragraph>每一项是一个 JUnit 动态测试，失败信息里写着哪里不对、怎么改。上线前、升级引擎后各跑一次。示例见 examples/spring-boot 的 FlowContractIT。</Paragraph>
    </Section>
    <Section id="checks" title="核对什么">
      <Tbl
        head={['项', '内容']}
        rows={[
          ['表', '引擎的表都在、列对得上：建表脚本执行到了最新'],
          ['组织架构', '查得到名字；角色的成员在 OrgDirectory.members 与 Actor.getRoles() 两边一致；有流程管理员'],
          ['扩展', '注册的扩展的 info() 写得对（名字、参数、结果）'],
          ['冒烟', '用你的组织架构和权限走一单：发起、成员在待办里看到、认领、处理、走完；跑完把冒烟流程停用'],
        ]}
      />
    </Section>
  </DocPage>
));
