package org.dpdns.zerodep.flow.core.view;

import com.easy.query.core.annotation.Column;
import lombok.Data;
import org.dpdns.zerodep.flow.core.db.JsonColumns;

import java.util.Map;

/** 列表的一行上带的实例（待办、抄送经导航 {@code instance} 一起读出）：标题、摘要列与状态，列表不用再一个个去查实例 */
@Data
public class InstanceRefVO {
    private Long id;
    /** 按哪个发布的版本走：处理结果的名字按它找 */
    private Long definitionId;
    private String title;
    /** 待办列表的摘要列：{ 引用名: 值 }（0005 第 2 节的 {@code summary}） */
    @Column(conversion = JsonColumns.ObjectJson.class)
    private Map<String, Object> summary;
    private String status;
}
