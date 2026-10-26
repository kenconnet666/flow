package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.identity.Actor;
import org.dpdns.zerodep.flow.core.store.WorkItemStore.Candidate;
import org.dpdns.zerodep.flow.model.definition.AssigneeSource;
import org.dpdns.zerodep.flow.model.definition.Ref;

import java.util.List;

/**
 * 谁算候选人、谁能发起：都按操作人的编号、角色、部门与扩展的归属去比（0007 的 1 节第 4 条：角色、部门的候选不展开成人）。
 */
final class CandidateRules {
    private CandidateRules() { }

    /** 候选里有我、我的角色、部门或扩展的归属，而且我没被排除（退回过收件箱的人不能再认领） */
    static boolean isCandidate(Actor actor, List<Candidate> candidates) {
        boolean excluded = candidates.stream().anyMatch(c -> Candidate.EXCLUDED.equals(c.kind()) && c.value().equals(actor.id()));
        if (excluded) return false;
        for (Candidate c : candidates) {
            boolean matches = switch (c.kind()) {
                case Candidate.USER -> c.value().equals(actor.id());
                case Candidate.ROLE -> actor.roles().contains(c.value());
                case Candidate.DEPARTMENT -> actor.departments().contains(c.value());
                case Candidate.EXCLUDED -> false;
                default -> actor.groups(c.kind()).contains(c.value());
            };
            if (matches) return true;
        }
        return false;
    }

    /**
     * 能不能发起（0005 第 2 节的 {@code starters}）：名单里的角色、部门、指定的人、扩展的种类，命中一个就行。
     * 发起时还没有表单与实例，引用（{@code { ref }}）取不到值，所以只认写死的值。
     */
    static boolean isStarter(Actor actor, List<AssigneeSource> starters) {
        if (starters.isEmpty()) return true;
        for (AssigneeSource source : starters) {
            if (source instanceof AssigneeSource.User user && !(user.user() instanceof Ref) && actor.id().equals(String.valueOf(user.user()))) {
                return true;
            }
            if (source instanceof AssigneeSource.Role role && actor.roles().contains(String.valueOf(role.role()))) return true;
            if (source instanceof AssigneeSource.Department dept && actor.departments().contains(String.valueOf(dept.department()))) return true;
            if (source instanceof AssigneeSource.Group group && actor.groups(group.kind()).contains(String.valueOf(group.id()))) return true;
        }
        return false;
    }
}
