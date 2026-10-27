package org.dpdns.zerodep.flow.core.view.instance;

import com.easy.query.core.annotation.Navigate;
import com.easy.query.core.enums.RelationTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * "抄送我的"的一行（0006 第 4 节的 {@code cc}）：抄送本身，加上经导航一起读出的实例（{@code selectAutoInclude(CcItem.class)}）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CcItem extends CcVO {
    /** 所在的实例 */
    @Navigate(value = RelationTypeEnum.ManyToOne)
    private InstanceRefVO instance;
}
