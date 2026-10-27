package org.dpdns.zerodep.flow.core.spi;

/**
 * 谁能看一个实例（0006 第 6 节）：打开实例详情、待办详情，调表单的 actions，评论之前问它；不能看时报 {@code flow.forbidden}。
 * <p>默认 {@link #participantsOr}：参与过的人总能看；没参与过的人看不看得到交给 {@link PermissionPolicy} 的
 * {@code instances.search}（管理端搜实例的权限，目标是流程标识）。权限默认全部放行，所以不配时谁都能看（0004 第 6 节的宽松原则）；
 * 接入方收紧了管理端的权限，没参与过的人也就打不开了。要别的规则（同部门的能看、按密级）时替换它。
 */
@FunctionalInterface
public interface ViewPolicy<U extends Actor> {
    /** 能不能看 */
    boolean canView(U actor, Viewing viewing);

    /** 参与过的人能看；别人要有 {@code instances.search} 的权限 */
    static <U extends Actor> ViewPolicy<U> participantsOr(PermissionPolicy<U> permissions) {
        return (actor, viewing) -> viewing.isParticipant() || permissions.allows(actor, "instances.search", viewing.getProcessKey());
    }
}
