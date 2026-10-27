package org.dpdns.zerodep.flow.core.form;

import java.util.Map;

/** 表单后端代码给前端调的一个接口（{@link FormHandler#actions()}）：返回的对象按 typed JSON 交给前端 */
@FunctionalInterface
public interface FormAction {
    Object call(Map<String, Object> args, FormContext ctx);
}
