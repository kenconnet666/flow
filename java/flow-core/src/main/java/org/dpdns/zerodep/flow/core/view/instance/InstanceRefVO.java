package org.dpdns.zerodep.flow.core.view.instance;

import com.easy.query.core.annotation.Column;
import lombok.Data;
import org.dpdns.zerodep.flow.core.db.JsonColumns;

import java.util.Map;

/** 列表的一行上带的实例（待办、抄送经导航 {@code instance} 一起读出）：标题、业务单号、摘要列、状态与结果，列表不用再一个个去查实例 */
@Data
public class InstanceRefVO {
    private Long id;
    /** 按哪个发布的版本走：处理结果的名字按它找 */
    private Long definitionId;
    private String title;
    /** 业务单号：接入方按它连自己的表（TodoQueryCustomizer） */
    private String businessKey;
    /** 待办列表的摘要列：{ 引用名: 值 }（0005 第 2 节的 {@code summary}） */
    @Column(conversion = JsonColumns.ObjectJson.class)
    private Map<String, Object> summary;
    private String status;
    /** 结束的结果（结束节点、终止的处理结果上写的，如 approved、rejected）；没结束时为 null */
    private String result;
}
