package org.dpdns.zerodep.flow.testkit;

import org.dpdns.zerodep.flow.core.spi.Actor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

/**
 * 接入方的契约测试：在自己的测试里继承它，给出 {@link #contract()}（连测试库的引擎和几个真实的样例账号），JUnit 把每一项核对
 * （{@link FlowContractChecks}）跑成一个测试，不过的那一项说哪里不对、怎么改。上线前、升级引擎后各跑一次。
 * <pre>{@code
 * @SpringBootTest
 * class FlowContractIT extends FlowContractTest<AppUser> {
 *     @Autowired FlowEngine<AppUser> engine;
 *     @Autowired EasyEntityQuery easyQuery;
 *     @Autowired OrgDirectory org;
 *     @Autowired AppUsers users;
 *
 *     @Override
 *     protected FlowContract<AppUser> contract() {
 *         return FlowContract.<AppUser>builder().engine(engine).db(easyQuery).org(org)
 *             .admin(users.byLogin("admin")).starter(users.byLogin("zhang")).role(11L).member(users.byLogin("li")).build();
 *     }
 * }
 * }</pre>
 */
@DisplayName("flow 契约核对")
public abstract class FlowContractTest<U extends Actor> {
    /** 接入方的引擎与样例账号 */
    protected abstract FlowContract<U> contract();

    @TestFactory
    Stream<DynamicTest> 契约核对() {
        return FlowContractChecks.of(contract()).stream().map(check -> DynamicTest.dynamicTest(check.getName(), check.getRun()::run));
    }
}
