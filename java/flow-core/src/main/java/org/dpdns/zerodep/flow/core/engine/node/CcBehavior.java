package org.dpdns.zerodep.flow.core.engine.node;

import org.dpdns.zerodep.flow.core.engine.Execution;
import org.dpdns.zerodep.flow.core.entity.CcEntity;
import org.dpdns.zerodep.flow.core.entity.EventEntity;
import org.dpdns.zerodep.flow.model.definition.ProcessNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 抄送节点（0005 第 4 节 {@code cc}）：抄送给节点上写的人（角色、部门展开成人），不停，接着往下走。
 * 处理人手动抄送（命令 {@code cc}）也用 {@link #copyTo}。
 */
public class CcBehavior {
    private CcBehavior() { }

    public static void arrive(Execution run, ProcessNode.Cc cc) {
        copyTo(run, cc, run.getAssignees().peopleOf(cc.getTo(), run.getInstance(), run::read), cc.getMessage());
        run.leave(cc.getId(), null);
    }

    /**
     * 抄送给这些人（同一个人只一条）：每人一条抄送（"抄送我的"里看到），记一条流转记录。
     *
     * @param node 抄送节点；手动抄送时为 null（抄送人记在抄送上）
     */
    public static void copyTo(Execution run, ProcessNode.Cc node, List<Long> users, String message) {
        List<Long> recipients = users.stream().distinct().toList();
        if (recipients.isEmpty()) return;
        Long fromId = node == null ? run.getActor().getId() : null;
        String fromName = node == null ? run.getActor().getName() : null;
        List<String> names = recipients.stream().map(run.getContext().getOrg()::userName).toList();
        List<CcEntity> rows = new ArrayList<>();
        for (int i = 0; i < recipients.size(); i++) {
            CcEntity cc = new CcEntity();
            cc.setInstanceId(run.getInstance().getId());
            cc.setNode(node == null ? null : node.getId());
            cc.setNodeName(node == null ? null : node.getName());
            cc.setRecipientId(recipients.get(i));
            cc.setRecipientName(names.get(i));
            cc.setFromUserId(fromId);
            cc.setFromUserName(fromName);
            cc.setMessage(message);
            cc.setCreatedAt(run.getNow());
            rows.add(cc);
        }
        run.getContext().getDb().insertable(rows).executeRows();
        run.addEvent(EventEntity.INSTANCE_CC, node, null, null, null, message, Map.of("users", recipients, "userNames", names));
    }
}
