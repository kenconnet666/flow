package org.dpdns.zerodep.flow.core.id;

/**
 * 主键策略（docs/decisions/0006 的 6.1）：编号都由应用在插入前生成，引擎里一律是字符串，落库的类型跟着策略走
 * （建表脚本里的编号列：雪花是 BIGINT，大雪花是 DECIMAL）。
 * 方法都带表名，个别表可以单独换策略。默认是雪花（{@link Snowflake}）；测试与场景用例用按顺序发号的 {@link SequentialIds}。
 */
public interface IdStrategy {
    /** 给出新编号 */
    String next(String table);

    /** 引擎里的编号 → 绑定到语句的值 */
    Object toDatabase(String id);

    /** 从结果读回的值 → 引擎里的编号 */
    String fromDatabase(Object value);
}
