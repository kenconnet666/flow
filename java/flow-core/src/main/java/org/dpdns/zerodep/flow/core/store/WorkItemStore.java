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
    /** 新建待办与它的候选，返回带上数据库给的编号的待办 */
    WorkItem insert(WorkItem item, List<Candidate> candidates);

    /** 读一个 */
    Optional<WorkItem> find(Long workItemId);

    /** 打开着的待办谁能处理、谁被排除 */
    List<Candidate> candidates(Long workItemId);

    /** 认领：只在打开着、还没有处理人时改到；返回是否改到 */
    boolean claim(Long workItemId, Claim claim);

    /** 处理：只在打开着时改到，并删掉候选；返回是否改到 */
    boolean complete(Long workItemId, Completion completion);

    /** 取消实例上全部打开着的待办并删掉候选，返回取消了的（取消之前的样子） */
    List<WorkItem> cancelOpen(Long instanceId, Instant at);

    /** 实例的全部待办，按到达的先后 */
    List<WorkItem> ofInstance(Long instanceId);

    /** 某个节点最后一次是谁处理的（{@code sameAs}）；没处理过时为空 */
    Optional<Long> lastCompletedBy(Long instanceId, String node);

    /** 收件箱：落到我的，加上我能认领的（候选里有我、我的角色或部门，且我没被排除），按到达时间从新到旧 */
    Page<WorkItem> todo(Member member, int page, int size);

    /**
     * 一行候选。
     *
     * @param kind     {@code user}、{@code role}、{@code department}、接入方扩展的种类，或 {@code excluded}（退回过收件箱，不能再认领）
     * @param targetId 用户、角色、部门的编号，按 kind 区分是哪一种
     */
    record Candidate(String kind, Long targetId) {
        /** 指定的人 */
        public static final String USER = "user";
        /** 角色 */
        public static final String ROLE = "role";
        /** 部门 */
        public static final String DEPARTMENT = "department";
        /** 被排除的人 */
        public static final String EXCLUDED = "excluded";
    }

    /**
     * 认领时写进待办的几列（属性名就是列名，默认存储直接 {@code set(claim)}）。
     *
     * @param assigneeId   认领的人
     * @param assigneeName 认领的人的名字（快照）
     * @param claimedAt    认领时间
     */
    record Claim(Long assigneeId, String assigneeName, Instant claimedAt) { }

    /**
     * 处理时写进待办的几列（属性名就是列名）。
     *
     * @param outcome         处理结果
     * @param comment         意见
     * @param completedBy     处理的人
     * @param completedByName 处理的人的名字（快照）
     * @param completedAt     处理时间
     */
    record Completion(String outcome, String comment, Long completedBy, String completedByName, Instant completedAt) { }

    /**
     * 拿来匹配候选的归属。
     *
     * @param userId      自己
     * @param memberships 候选种类 → 我在这一种里的编号（{@code user} → 自己，{@code role} → 我的角色……）
     */
    record Member(Long userId, Map<String, Set<Long>> memberships) { }
}
