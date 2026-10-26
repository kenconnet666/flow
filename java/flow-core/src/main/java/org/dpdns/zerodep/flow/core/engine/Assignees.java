package org.dpdns.zerodep.flow.core.engine;

import org.dpdns.zerodep.flow.core.store.WorkItemStore.Candidate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 一个人工节点解析出来的处理人（0005 的 3.2）：几个来源取并集，分成"人"和"收件箱"（角色、部门）两类。
 * 只有一个人、没有收件箱时直接落到这个人；否则进收件箱，人和收件箱都作为候选，谁先认领谁办。
 *
 * @param users   解析出的人（用户编号），保持先后、去重
 * @param inboxes 收件箱：角色、部门的候选
 */
record Assignees(Set<Long> users, List<Candidate> inboxes) {
    /** 空的一份，解析时往里加 */
    static Assignees empty() { return new Assignees(new LinkedHashSet<>(), new ArrayList<>()); }

    /** 只有这一个人 */
    static Assignees of(Long user) {
        Assignees one = empty();
        one.users().add(user);
        return one;
    }

    /** 只留收件箱（提级：进收件箱，不直接落到人） */
    Assignees inboxesOnly() { return new Assignees(new LinkedHashSet<>(), new ArrayList<>(inboxes)); }

    /** 一个也没有：按定义的 {@code emptyAssignee} 处理 */
    boolean isEmpty() { return users.isEmpty() && inboxes.isEmpty(); }

    /** 直接落到一个人：只有一个人，也没有收件箱 */
    boolean isDirect() { return users.size() == 1 && inboxes.isEmpty(); }

    /** 直接落到的那个人 */
    Long directUser() { return users.iterator().next(); }

    /** 第一个收件箱（待办上显示它属于哪个角色、部门）；没有时为 null */
    Candidate firstInbox() { return inboxes.isEmpty() ? null : inboxes.get(0); }

    /** 进收件箱时要存的候选：人在前，收件箱在后 */
    List<Candidate> candidates() {
        List<Candidate> out = new ArrayList<>();
        for (Long user : users) out.add(new Candidate(Candidate.USER, user));
        out.addAll(inboxes);
        return out;
    }
}
