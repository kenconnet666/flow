package org.dpdns.zerodep.flow.core.form;

import org.dpdns.zerodep.flow.core.data.FormValues;

import java.util.Map;

/**
 * 表单的后端代码（0004 的 1.5）：在表单编辑器的"后端代码"页签里写，跟表单版本一起发布，引擎在固定的时机调它。每个方法都可选。
 * <pre>{@code
 * import org.dpdns.zerodep.flow.core.form.*;
 * import org.dpdns.zerodep.flow.core.data.FormValues;
 *
 * public class PurchaseForm implements FormHandler {
 *     @Autowired BudgetService budgets;   // 接入方自己的 Spring Bean，照常注入
 *
 *     @Override
 *     public FieldErrors validate(FormValues values, FormContext ctx) {
 *         return values.decimal("amount").compareTo(budgets.remaining()) > 0 ? FieldErrors.of("amount", "超出预算") : FieldErrors.none();
 *     }
 * }
 * }</pre>
 * 调用的时机：
 * <pre>
 *   load      发起页打开时：带出初始值（盖在清单的默认值上）
 *   validate  发起、处理提交时，清单与字段权限校验通过之后：后端才能做的校验（查预算、查重）
 *   compute   validate 通过之后：后端算的字段（合计），覆盖前端交上来的值
 *   submit    最后，和流转在同一个事务里：写业务表；抛错整个回滚
 *   actions   给前端调的接口：表单代码里 context.call(名字, 参数)
 * </pre>
 * 暂存只做清单校验，不调这里。
 */
public interface FormHandler {
    /** 发起页的初始值：字段名 → 值（按清单的字段类型） */
    default FormValues load(FormContext ctx) { return FormValues.empty(); }

    /** 后端校验：有问题时返回按字段的错误，整个提交不做 */
    default FieldErrors validate(FormValues values, FormContext ctx) { return FieldErrors.none(); }

    /** 后端算的字段：返回的值覆盖提交的（只写要覆盖的字段） */
    default FormValues compute(FormValues values, FormContext ctx) { return FormValues.empty(); }

    /** 提交：与流转同一个事务，写业务表；抛错整个命令回滚 */
    default void submit(FormValues values, FormContext ctx) { }

    /** 给前端调的接口：名字 → 做什么。前端 {@code context.call(名字, 参数)}，能看这个实例的人才能调 */
    default Map<String, FormAction> actions() { return Map.of(); }
}
