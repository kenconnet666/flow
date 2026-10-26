package org.dpdns.zerodep.flow.core.view;

import java.util.List;

/**
 * 提交时要选处理人的下一步（0005 的 5.6 {@code pick}）：审批面板为它显示一个选人框。
 *
 * @param node       要选人的节点
 * @param nodeName   节点名称
 * @param max        最多选几个；null 时不限
 * @param candidates 能选的人；null 时谁都可以选（人员选择器的数据由接入方给）
 */
public record PickView(String node, String nodeName, Integer max, List<Candidate> candidates) {
    /**
     * 能选的一个人。
     *
     * @param id   用户编号
     * @param name 名字
     */
    public record Candidate(Long id, String name) { }
}
