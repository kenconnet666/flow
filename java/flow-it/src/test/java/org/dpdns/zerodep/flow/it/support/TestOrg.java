package org.dpdns.zerodep.flow.it.support;

import org.dpdns.zerodep.flow.core.identity.OrgDirectory;

import java.util.Map;

/**
 * 测试里的组织架构：用户编号 → 名字。接入方实现 {@link OrgDirectory} 时接自己的用户表。
 *
 * @param names 用户编号 → 名字
 */
public record TestOrg(Map<Long, String> names) implements OrgDirectory {
    @Override
    public String userName(Long userId) { return names.getOrDefault(userId, String.valueOf(userId)); }
}
