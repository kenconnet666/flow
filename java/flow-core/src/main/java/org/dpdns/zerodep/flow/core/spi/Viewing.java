package org.dpdns.zerodep.flow.core.spi;

/** {@link ViewPolicy} 拿到的：要看的是哪个实例，操作人参与过没有 */
public interface Viewing {
    Long getInstanceId();

    String getProcessKey();

    /** 发起人 */
    Long getStartedBy();

    /**
     * 操作人参与过：是发起人、流程管理员，或者在这个实例上处理过、正在处理、是待办的候选、委派过、要指定处理人、被抄送过、流转记录里出现过。
     * 第一次问时才查库（几条带 LIMIT 1 的查询），规则用不上它时不查。
     */
    boolean isParticipant();
}
