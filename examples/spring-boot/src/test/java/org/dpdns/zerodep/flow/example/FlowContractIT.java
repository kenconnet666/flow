package org.dpdns.zerodep.flow.example;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import org.dpdns.zerodep.flow.core.FlowEngine;
import org.dpdns.zerodep.flow.core.identity.OrgDirectory;
import org.dpdns.zerodep.flow.testkit.FlowContract;
import org.dpdns.zerodep.flow.testkit.FlowContractTest;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 示例应用的契约核对：接入方照这样写一个，连测试库跑（上线前、升级引擎后）。样例账号取示例组织架构里的：
 * 总经理（1004）是管理员，林小满（1001）发起，财务角色（1）的周启航（1002）认领处理。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_URL", matches = ".+")
class FlowContractIT extends FlowContractTest<ExampleUser> {
    @Autowired
    FlowEngine<ExampleUser> engine;
    @Autowired
    EasyEntityQuery easyQuery;
    @Autowired
    OrgDirectory org;

    @Override
    protected FlowContract<ExampleUser> contract() {
        return FlowContract.<ExampleUser>builder().engine(engine).db(easyQuery).org(org)
            .admin(ExampleOrgDirectory.USERS.get(1004L)).starter(ExampleOrgDirectory.USERS.get(1001L))
            .role(1L).member(ExampleOrgDirectory.USERS.get(1002L)).build();
    }
}
