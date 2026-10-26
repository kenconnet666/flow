package org.dpdns.zerodep.flow.core.store;

import org.dpdns.zerodep.flow.model.runtime.WorkItem;
import org.dpdns.zerodep.query.exec.Page;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 待办与候选（0007 的 {@code flow_work_item}、{@code flow_candidate}）。候选只在待办打开时存在：处理完、取消时一并删掉。
 * 状态的变化都写成带条件的更新（改到 0 行就是别人先改了），所以认领先到先得、重复提交挡得住。默认实现是 {@link QueryStores}。
 */
public interface WorkItemStore {
    /** 新建待办与它的候选 */
    void insert(WorkItem item, List<Candidate> candidates);

    /** 读一个 */
    Optional<WorkItem> find(String workItemId);

    /** 打开着的待办谁能处理、谁被排除 */
    List<Candidate> candidates(String workItemId);

    /** 认领：只在打开着、还没有处理人时改到；返回是否改到 */
    boolean claim(String workItemId, String userId, String userName, Instant at);

    /** 处理：只在打开着时改到，并删掉候选；返回是否改到 */
    boolean complete(String workItemId, Completion completion);

    /** 取消实例上全部打开着的待办并删掉候选，返回取消了的（取消之前的样子） */
    List<WorkItem> cancelOpen(String instanceId, Instant at);

    /** 实例的全部待办，按到达的先后 */
    List<WorkItem> ofInstance(String instanceId);

    /** 某个节点最后一次是谁处理的（{@code sameAs}）；没处理过时为空 */
    Optional<String> lastCompletedBy(String instanceId, String node);

    /** 收件箱：落到我的，加上我能认领的（候选里有我、我的角色或部门，且我没被排除），按到达时间从新到旧 */
    Page<WorkItem> todo(Member member, int page, int size);

    /**
     * 一行候选。
     *
     * @param kind  {@code user}、{@code role}、{@code department}、接入方扩展的种类，或 {@code excluded}（退回过收件箱，不能再认领）
     * @param value 用户、角色、部门的编号
     */
    record Candidate(String kind, String value) {
        /** 指定的人 */
        public static final String USER = "user";
        /** 角色 */
        public static final String ROLE = "role";
        /** 部门 */
        public static final String DEPARTMENT = "department";
        /** 被排除的人 */
        public static final String EXCLUDED = "excluded";
    }

    /** 处理的结果 */
    record Completion(String outcome, String comment, String userId, String userName, Instant at) { }

    /** 拿来匹配候选的归属：候选种类 → 编号；含 {@code user} → 自己 */
    record Member(String userId, Map<String, Set<String>> memberships) { }
}
