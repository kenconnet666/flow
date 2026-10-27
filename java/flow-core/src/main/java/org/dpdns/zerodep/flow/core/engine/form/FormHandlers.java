package org.dpdns.zerodep.flow.core.engine.form;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import org.dpdns.zerodep.flow.core.entity.FormVersionEntity;
import org.dpdns.zerodep.flow.core.extension.FormHandlerFactory;
import org.dpdns.zerodep.flow.core.form.FormHandler;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 表单版本的后端代码：第一次用到时编译、创建，按表单版本记住。发布的版本不会变，所以缓存不用失效；
 * 集群里别的节点发布了新版本，这里第一次用到新版本时自己编译，不需要通知。
 */
public class FormHandlers {
    private final EasyEntityQuery db;
    private final FormCompiler compiler;
    private final FormHandlerFactory factory;
    private final Map<Long, Optional<FormHandler>> loaded = new ConcurrentHashMap<>();

    public FormHandlers(EasyEntityQuery db, FormCompiler compiler, FormHandlerFactory factory) {
        this.db = db;
        this.compiler = compiler;
        this.factory = factory;
    }

    /** 一个表单版本的后端代码；没有后端代码时为空 */
    public Optional<FormHandler> of(Long formVersionId) {
        Optional<FormHandler> found = loaded.get(formVersionId);
        if (found != null) return found;
        Optional<FormHandler> created = create(formVersionId);
        loaded.putIfAbsent(formVersionId, created);
        return loaded.get(formVersionId);
    }

    private Optional<FormHandler> create(Long formVersionId) {
        FormVersionEntity version = db.queryable(FormVersionEntity.class).whereById(formVersionId)
            .select(v -> v.FETCHER.id().formKey().version().serverLanguage().serverSource())
            .singleOrNull();
        if (version == null || !"java".equals(version.getServerLanguage()) || version.getServerSource() == null
            || version.getServerSource().isBlank()) {
            return Optional.empty();
        }
        FormCompiler.Result result = compiler.compile(version.getServerSource());
        if (!result.isOk()) {
            // 发布时编译过；到这里还编不过是运行环境变了（少了依赖的类）：这张表单不可用
            throw new FlowException(FlowErrorCode.EXTENSION_FAILED,
                "form " + version.getFormKey() + "@" + version.getVersion() + " backend code does not compile: " + result.getProblems(),
                Map.of("kind", "form", "name", version.getFormKey()), java.util.List.of(), null);
        }
        return Optional.of(factory.create(result.getType()));
    }
}
