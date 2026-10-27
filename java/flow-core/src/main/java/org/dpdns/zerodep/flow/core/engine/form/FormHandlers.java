package org.dpdns.zerodep.flow.core.engine.form;

import com.easy.query.api.proxy.client.EasyEntityQuery;
import lombok.Getter;
import org.dpdns.zerodep.flow.core.entity.FormVersionEntity;
import org.dpdns.zerodep.flow.core.spi.FormHandler;
import org.dpdns.zerodep.flow.core.spi.FormHandlerFactory;
import org.dpdns.zerodep.flow.model.error.FlowErrorCode;
import org.dpdns.zerodep.flow.model.error.FlowException;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 表单版本的后端代码：第一次用到时编译、创建，按表单版本记住。发布的版本不会变，所以缓存不用失效；
 * 集群里别的节点发布了新版本，这里第一次用到新版本时自己编译，不需要通知。
 * <p>
 * 在线写的后端代码等于让有表单发布权限的人在服务器上跑 Java：生产环境不想要时关掉（{@code serverCode = false}），
 * 发布带后端代码的表单报错，已经存了后端代码的表单版本用到时也报错，不悄悄跳过。
 */
public class FormHandlers {
    private final EasyEntityQuery db;
    private final FormCompiler compiler;
    private final FormHandlerFactory factory;
    /** 允许在线写的后端代码 */
    @Getter
    private final boolean serverCode;
    private final Map<Long, Optional<FormHandler>> loaded = new ConcurrentHashMap<>();

    public FormHandlers(EasyEntityQuery db, FormCompiler compiler, FormHandlerFactory factory, boolean serverCode) {
        this.db = db;
        this.compiler = compiler;
        this.factory = factory;
        this.serverCode = serverCode;
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
        if (!serverCode) {
            throw new FlowException(FlowErrorCode.EXTENSION_FAILED,
                "form " + version.getFormKey() + "@" + version.getVersion() + " has backend code but backend code is disabled",
                Map.of("kind", "form", "name", version.getFormKey(), "reason", "server-code-disabled"), java.util.List.of(), null);
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
