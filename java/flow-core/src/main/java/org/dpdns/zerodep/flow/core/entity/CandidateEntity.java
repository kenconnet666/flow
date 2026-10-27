package org.dpdns.zerodep.flow.core.entity;

import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import lombok.NoArgsConstructor;
import org.dpdns.zerodep.flow.core.entity.proxy.CandidateEntityProxy;

/**
 * 打开着的待办谁能处理、谁被排除（{@code flow_candidate}）：角色、部门存一行（不展开成人），待办结束时删掉。
 */
@Getter
@Setter
@FieldNameConstants
@NoArgsConstructor
@Table("flow_candidate")
@EntityProxy
public class CandidateEntity implements ProxyEntityAvailable<CandidateEntity, CandidateEntityProxy>, Tenanted {
    /** 指定的人 */
    public static final String USER = "user";
    /** 角色 */
    public static final String ROLE = "role";
    /** 部门 */
    public static final String DEPARTMENT = "department";
    /** 被排除的人（退回过收件箱，不能再认领） */
    public static final String EXCLUDED = "excluded";

    @Column(primaryKey = true, generatedKey = true)
    private Long id;
    private Long tenantId;
    private Long workItemId;
    /** {@link #USER}、{@link #ROLE}、{@link #DEPARTMENT}、接入方扩展的种类，或 {@link #EXCLUDED} */
    private String kind;
    /** 用户、角色、部门的编号，按 kind 区分是哪一种 */
    private Long targetId;

    /** 还没挂到待办上的一行（插入时填 workItemId） */
    public CandidateEntity(String kind, Long targetId) {
        this.kind = kind;
        this.targetId = targetId;
    }
}
