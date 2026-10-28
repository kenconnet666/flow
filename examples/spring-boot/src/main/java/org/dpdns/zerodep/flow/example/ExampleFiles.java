package org.dpdns.zerodep.flow.example;

import lombok.Data;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 示例的附件存取：表单里附件字段的值只记 {@code { id, name, size }}，文件本身由接入方存（0004 的表格）。
 * <b>只为演示</b>：存在临时目录、不查权限，重置演示时清空；真实应用存到对象存储或文件服务，下载时按"能不能看这个实例"把关。
 * 前端经 {@code provideFlowFiles} 接上这两个接口（示例前端的 files.ts）。
 */
@RestController
public class ExampleFiles {
    private static final Path ROOT = Path.of(System.getProperty("java.io.tmpdir"), "flow-demo-files");

    /** 存好的一个文件：表单的值就是它 */
    @Data
    public static class StoredFile {
        private final String id;
        private final String name;
        private final long size;
    }

    /** 上传：multipart 的 file */
    @PostMapping("/demo/files")
    public StoredFile upload(@RequestParam("file") MultipartFile file) throws IOException {
        Files.createDirectories(ROOT);
        String id = UUID.randomUUID().toString();
        String name = file.getOriginalFilename() == null ? id : file.getOriginalFilename();
        file.transferTo(ROOT.resolve(id));
        Files.writeString(ROOT.resolve(id + ".name"), name, StandardCharsets.UTF_8);
        return new StoredFile(id, name, file.getSize());
    }

    /** 删掉上传过的全部文件（重置演示时） */
    static void clear() throws IOException {
        if (!Files.exists(ROOT)) return;
        try (var files = Files.list(ROOT)) {
            for (Path file : files.toList()) Files.deleteIfExists(file);
        }
    }

    /** 下载：按上传时的文件名 */
    @GetMapping("/demo/files/{id}")
    public ResponseEntity<FileSystemResource> download(@PathVariable("id") String id) throws IOException {
        // 编号只能是上传时给的 UUID：挡住 ../ 这类路径
        if (!id.matches("[0-9a-f-]{36}") || !Files.exists(ROOT.resolve(id))) return ResponseEntity.notFound().build();
        String name = Files.readString(ROOT.resolve(id + ".name"), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(name, StandardCharsets.UTF_8).build().toString())
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(new FileSystemResource(ROOT.resolve(id)));
    }
}
