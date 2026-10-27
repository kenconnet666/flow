package org.dpdns.zerodep.flow.core.spi;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 一个选项：参数的可选值、动作或自定义节点可能给的结果 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Choice {
    /** 写进定义的值 */
    private String value;
    /** 设计器里显示的字 */
    private String label;
}
