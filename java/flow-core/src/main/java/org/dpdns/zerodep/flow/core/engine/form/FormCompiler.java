package org.dpdns.zerodep.flow.core.engine.form;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.dpdns.zerodep.flow.core.spi.FormHandler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * 编译表单的后端代码（0004 的 1.5）：JDK 自带的 {@code javax.tools.JavaCompiler}，源码与编译出的类都在内存里，
 * 每次编译一个新的类加载器（换版本时丢掉旧的）。代码是一个 public 类、不写 package、实现 {@link FormHandler}。
 * <p>运行时要有 {@code jdk.compiler} 模块（用 JDK 运行，不能只有 JRE）。classpath 取应用的 {@code java.class.path}
 * 与类加载器能列出的 jar；Spring Boot 打成可执行 jar（嵌套 jar）时还要另做文件管理器，见 0004 的"待实测"。
 */
public class FormCompiler {
    private static final Pattern PUBLIC_CLASS = Pattern.compile("public\\s+(?:final\\s+)?class\\s+(\\w+)");

    /** 编译出的一个问题：行列从 1 开始 */
    @Data
    @AllArgsConstructor
    public static class Problem {
        private final long line;
        private final long column;
        private final String message;
    }

    /** 编译的结果：成功时有类，失败时有问题 */
    @Data
    @AllArgsConstructor
    public static class Result {
        private final Class<? extends FormHandler> type;
        private final List<Problem> problems;

        public boolean isOk() { return type != null; }
    }

    /** 编译一份后端代码 */
    public Result compile(String source) {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null) {
            throw new IllegalStateException("form backend code needs the jdk.compiler module: run the application on a JDK, not a JRE");
        }
        Matcher name = PUBLIC_CLASS.matcher(source);
        if (!name.find()) return failed(1, 1, "the code needs one public class that implements FormHandler");
        String className = name.group(1);

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager standard = javac.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8);
        MemoryFiles files = new MemoryFiles(standard);
        List<String> options = List.of("-classpath", classpath(), "-proc:none", "-encoding", "UTF-8");
        boolean compiled = javac.getTask(null, files, diagnostics, options, null, List.of(new Source(className, source))).call();
        List<Problem> problems = new ArrayList<>();
        for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
            if (d.getKind() == Diagnostic.Kind.ERROR) problems.add(new Problem(d.getLineNumber(), d.getColumnNumber(), d.getMessage(null)));
        }
        if (!compiled) return new Result(null, problems);

        try {
            Class<?> type = new MemoryClassLoader(files.classes, parentLoader()).loadClass(className);
            if (!FormHandler.class.isAssignableFrom(type)) return failed(1, 1, className + " does not implement FormHandler");
            return new Result(type.asSubclass(FormHandler.class), List.of());
        } catch (ClassNotFoundException e) {
            return failed(1, 1, "compiled class " + className + " not found");
        }
    }

    private static Result failed(long line, long column, String message) {
        return new Result(null, List.of(new Problem(line, column, message)));
    }

    /** 能看到应用的类：线程的类加载器（Spring 的应用类加载器），没有时用引擎自己的 */
    private static ClassLoader parentLoader() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        return context != null ? context : FormHandler.class.getClassLoader();
    }

    /** 编译时的 classpath：java.class.path 加上类加载器能列出的 jar、目录 */
    private static String classpath() {
        Set<String> entries = new LinkedHashSet<>(List.of(System.getProperty("java.class.path", "").split(File.pathSeparator)));
        for (ClassLoader loader = parentLoader(); loader != null; loader = loader.getParent()) {
            if (!(loader instanceof URLClassLoader urls)) continue;
            for (URL url : urls.getURLs()) {
                if ("file".equals(url.getProtocol())) entries.add(new File(URI.create(url.toString())).getPath());
            }
        }
        entries.remove("");
        return String.join(File.pathSeparator, entries);
    }

    /** 内存里的源码 */
    private static class Source extends SimpleJavaFileObject {
        private final String code;

        Source(String className, String code) {
            super(URI.create("string:///" + className + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) { return code; }
    }

    /** 编译出的类写进内存 */
    private static class MemoryFiles extends ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, ByteArrayOutputStream> classes = new LinkedHashMap<>();

        MemoryFiles(StandardJavaFileManager standard) { super(standard); }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
            return new SimpleJavaFileObject(URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind) {
                @Override
                public OutputStream openOutputStream() {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    classes.put(className, out);
                    return out;
                }
            };
        }
    }

    /** 从内存里取编译出的类；别的类交给应用的类加载器 */
    private static class MemoryClassLoader extends ClassLoader {
        private final Map<String, ByteArrayOutputStream> classes;

        MemoryClassLoader(Map<String, ByteArrayOutputStream> classes, ClassLoader parent) {
            super(parent);
            this.classes = classes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            ByteArrayOutputStream bytes = classes.get(name);
            if (bytes == null) throw new ClassNotFoundException(name);
            byte[] code = bytes.toByteArray();
            return defineClass(name, code, 0, code.length);
        }
    }
}
