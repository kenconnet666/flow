package org.dpdns.zerodep.flow.core.view.workitem;

import com.easy.query.core.annotation.Navigate;
import com.easy.query.core.enums.RelationTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dpdns.zerodep.flow.core.view.instance.InstanceRefVO;

import java.util.Map;

/**
 * 待办列表的一行（0006 第 4 节的 {@code todo}、{@code done}）：待办本身，加上经导航一起读出的实例
 * （{@code selectAutoInclude(TodoItem.class)}）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TodoItem extends WorkItemVO {
    /** 所在的实例 */
    @Navigate(value = RelationTypeEnum.ManyToOne)
    private InstanceRefVO instance;
    /** 处理结果的名字（按实例的定义版本找）；还没处理时为 null */
    private String outcomeLabel;
    /** 接入方补的业务列（{@code TodoQueryCustomizer.columns}）；没补时为 null */
    private Map<String, Object> business;
}
