package org.dpdns.zerodep.flow.it.contract;

import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.spi.ExtensionInfo;
import org.dpdns.zerodep.flow.core.spi.ExtensionParam;
import org.dpdns.zerodep.flow.core.spi.PeopleResolver;
import org.dpdns.zerodep.flow.it.support.LiveMysql;
import org.dpdns.zerodep.flow.it.support.TestOrg;
import org.dpdns.zerodep.flow.it.support.TestUser;
import org.dpdns.zerodep.flow.testkit.FlowContract;
import org.dpdns.zerodep.flow.testkit.FlowContractChecks;
import org.dpdns.zerodep.flow.testkit.FlowContractTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 契约测试套件：本仓库的测试组织架构照接入方的用法跑一遍（应当全过），再故意接错几样看报得出来 */
@DisplayName("契约测试套件")
class ContractKitTest extends FlowContractTest<TestUser> {
    private static final TestUser ADMIN = TestUser.of(1000L, "管理员");
    private static final TestUser ALICE = TestUser.of(1001L, "林小满");
    private static final TestUser CAROL = TestUser.of(1003L, "沈知远", 7L);
    private static final List<TestUser> EVERYONE = List.of(ADMIN, ALICE, CAROL);

    @BeforeAll
    static void freshTables() {
        LiveMysql.recreateTables();
    }

    @Override
    protected FlowContract<TestUser> contract() {
        return contract(new TestOrg(EVERYONE), CAROL, List.of());
    }

    private static FlowContract<TestUser> contract(TestOrg org, TestUser member, List<PeopleResolver> resolvers) {
        var db = LiveMysql.easyQuery();
        var builder = FlowEngine.<TestUser>builder(db).orgDirectory(org);
        resolvers.forEach(builder::resolver);
        return FlowContract.<TestUser>builder().engine(builder.build()).db(db).org(org)
            .admin(ADMIN).starter(ALICE).role(7L).member(member).build();
    }

    /** 跑名字是 name 的那一项 */
    private static void run(FlowContract<TestUser> contract, String name) {
        FlowContractChecks.of(contract).stream().filter(c -> c.getName().equals(name)).findFirst().orElseThrow().getRun().run();
    }

    @Nested
    @DisplayName("接错了报得出来")
    class Broken {
        @Test
        void 成员的角色与组织架构对不上() {
            TestUser stranger = TestUser.of(1003L, "沈知远");   // Actor 上没有角色 7
            AssertionError error = assertThrows(AssertionError.class, () -> run(contract(new TestOrg(EVERYONE), stranger, List.of()), "组织架构：角色的成员两边对得上"));
            assertTrue(error.getMessage().contains("Actor.getRoles()"), error.getMessage());
        }

        @Test
        void 查不到名字() {
            TestOrg missing = new TestOrg(List.of(ADMIN, CAROL));   // 发起人不在组织架构里
            AssertionError error = assertThrows(AssertionError.class, () -> run(contract(missing, CAROL, List.of()), "组织架构：查得到人的名字"));
            assertTrue(error.getMessage().contains("1001"), error.getMessage());
        }

        @Test
        void 扩展的声明写错() {
            PeopleResolver sloppy = new PeopleResolver() {
                @Override public String name() { return "sloppy"; }

                @Override public List<Long> resolve(Context context) { return List.of(); }

                @Override
                public ExtensionInfo info() {
                    return ExtensionInfo.of("写错的").param(ExtensionParam.of("level", "级", "integer")).param(ExtensionParam.select("kind", "种类"));
                }
            };
            AssertionError error = assertThrows(AssertionError.class, () -> run(contract(new TestOrg(EVERYONE), CAROL, List.of(sloppy)), "扩展的声明写得对"));
            assertTrue(error.getMessage().contains("integer") && error.getMessage().contains("没有选项"), error.getMessage());
        }

        @Test
        void 没有流程管理员() {
            TestOrg noAdmin = new TestOrg(List.of(ALICE, CAROL)) {
                @Override public List<Long> administrators() { return List.of(); }
            };
            assertThrows(AssertionError.class, () -> run(contract(noAdmin, CAROL, List.of()), "组织架构：有流程管理员"));
        }
    }
}
