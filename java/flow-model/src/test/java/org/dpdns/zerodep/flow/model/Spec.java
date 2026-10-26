package org.dpdns.zerodep.flow.model;

import org.dpdns.zerodep.wire.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** 读仓库根目录 spec/ 下两个后端共用的样例（测试的工作目录是模块目录 java/flow-model） */
final class Spec {
    private static final Path ROOT = Path.of("..", "..", "spec");

    private Spec() { }

    /** 读一份 JSON 样例，路径相对 spec/ */
    static Map<?, ?> read(String path) {
        try {
            return (Map<?, ?>) Json.read(Files.readString(ROOT.resolve(path), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read spec/" + path, e);
        }
    }
}
