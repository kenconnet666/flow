package org.dpdns.zerodep.flow.it.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

/** 逐个文件跑 spec/scenarios 下的场景用例（两个后端共同的验收标准）；新加一个场景只加一个 JSON 文件 */
@DisplayName("场景用例（spec/scenarios）")
class ScenarioTest {
    static List<Scenario> scenarios() {
        return Scenario.files().stream().map(Scenario::read).toList();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void runs(Scenario scenario) {
        new ScenarioRunner(scenario).run();
    }
}
