package org.dpdns.zerodep.flow.core.store;

import org.dpdns.zerodep.flow.core.data.FormValues;

import java.util.Optional;

/**
 * 实例上表单的值（0007 的 {@code flow_form_data}，每个实例一行）。默认实现在 {@code store.query} 包里，可以单独替换。
 */
public interface FormDataStore {
    /** 实例的表单值；流程没有表单时为空 */
    Optional<StoredFormData> read(Long instanceId);

    /** 发起时写第一份 */
    void insert(Long instanceId, Long formVersionId, FormValues values);

    /** 处理时整份写回（调用前已锁住实例行） */
    void update(Long instanceId, FormValues values);

    /**
     * 存着的一份表单值。
     *
     * @param formVersionId 填的是哪个表单版本
     * @param values        表单的值
     */
    record StoredFormData(Long formVersionId, FormValues values) { }
}
