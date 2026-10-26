package org.dpdns.zerodep.flow.core.store;

import java.time.Instant;
import java.util.Optional;

/**
 * 流程本身与发布的版本（0007 的 {@code flow_process}、{@code flow_definition}）。默认实现是 {@link QueryStores}，可以单独替换（0006 第 6 节）。
 */
public interface DefinitionStore {
    /**
     * 发布：流程还没有时建上，最新版本号加一，存一份不可变的版本。两个人同时发布同一个流程时，后提交的在唯一键上失败。
     *
     * @return 新的版本
     */
    StoredDefinition publish(Publication publication);

    /** 流程本身；还没发布过时为空 */
    Optional<StoredProcess> process(String processKey);

    /** 最新发布的版本 */
    Optional<StoredDefinition> latest(String processKey);

    /** 按编号取一个版本 */
    Optional<StoredDefinition> byId(Long definitionId);

    /** 发布一个版本要的东西；{@code model} 是 0005 的整份定义的 JSON 文字 */
    record Publication(String processKey, String name, String category, String model, String formKey, Integer formVersion,
                       String note, String publishedBy, String publishedByName, Instant at) { }

    /** 流程本身：启用停用与最新版本 */
    record StoredProcess(String processKey, String name, String status, int latestVersion) {
        /** 能发起 */
        public static final String ACTIVE = "active";
    }

    /** 发布的一个版本；formKey、formVersion 是发布时定下的表单版本 */
    record StoredDefinition(Long id, String processKey, int version, String model, String formKey, Integer formVersion) { }
}
