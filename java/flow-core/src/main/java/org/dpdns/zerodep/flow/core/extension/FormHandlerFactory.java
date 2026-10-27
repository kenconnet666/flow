package org.dpdns.zerodep.flow.core.extension;

import org.dpdns.zerodep.flow.core.form.FormHandler;

/**
 * 表单后端代码的类怎么变成对象（0004 的 1.5）：默认用无参构造；Spring 里换成 {@code AutowireCapableBeanFactory.createBean}，
 * 后端代码里的 {@code @Autowired} 照常注入接入方的 Bean。
 */
@FunctionalInterface
public interface FormHandlerFactory {
    /** 默认：无参构造 */
    FormHandlerFactory REFLECTION = type -> {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot create " + type.getName() + ": it needs a public no-argument constructor", e);
        }
    };

    FormHandler create(Class<? extends FormHandler> type);
}
