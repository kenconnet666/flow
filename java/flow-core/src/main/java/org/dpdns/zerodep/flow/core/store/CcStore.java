package org.dpdns.zerodep.flow.core.store;

import org.dpdns.zerodep.flow.model.runtime.Cc;
import org.dpdns.zerodep.query.exec.Page;

import java.time.Instant;
import java.util.List;

/**
 * 抄送（0007 的 {@code flow_cc}）：抄送节点、处理人手动抄送，每个收到的人一行。默认实现是
 * {@link org.dpdns.zerodep.flow.core.store.query.QueryStores}。
 */
public interface CcStore {
    /** 要写的一条抄送（编号、租户由存储填；还没读） */
    record NewCc(Long instanceId, String node, String nodeName, Long recipientId, String recipientName, Long fromUserId,
                 String fromUserName, String message, Instant createdAt) { }

    /** 写几条 */
    void insert(List<NewCc> ccs);

    /** 抄送给这个人的：未读在前，已读的按读的时间从新到旧，同样的按抄送时间从新到旧 */
    Page<Cc> received(Long recipientId, int page, int size);

    /** 这个人把一个实例抄送给他的都标成已读，返回标了几条 */
    int markRead(Long instanceId, Long recipientId, Instant at);
}
