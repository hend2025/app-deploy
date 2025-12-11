package com.aeye.app.deploy.controller;

import com.aeye.app.deploy.config.DirectoryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 文件浏览器控制器
 * 提供 home-directory 目录的文件浏览、查看和下载功能
 */
@RestController
@RequestMapping("/fileBrowser")
public class FileBrowserController {

    private static final Logger logger = LoggerFactory.getLogger(FileBrowserController.class);

    @Autowired
    private DirectoryConfig directoryConfig;

    /** 可预览的文本文件扩展名 */
    private static final Set<String> TEXT_EXTENSIONS = new HashSet<>(Arrays.asList(
        "txt", "log", "sh", "bat", "cmd", "ps1", "py", "java", "js", "ts", "json", 
        "xml", "yml", "yaml", "properties", "conf", "cfg", "ini", "md", "html", 
        "css", "sql", "vue", "jsx", "tsx", "go", "rs", "c", "cpp", "h", "hpp"
    ));

    /**
     * 获取目录内容
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listDirectory(@RequestParam(defaultValue = "") String path) {
        Map<String, Object> response = new HashMap<>();
        try {
            String homeDir = directoryConfig.getHomeDirectory();
            Path basePath = Paths.get(homeDir).normalize();
            Path targetPath = basePath.resolve(path).normalize();

            // 安全检查：确保目标路径在 home 目录内
            if (!targetPath.startsWith(basePath)) {
                response.put("success", false);
                response.put("message", "非法路径");
                return ResponseEntity.ok(response);
            }

            if (!Files.exists(targetPath) || !Files.isDirectory(targetPath)) {
                response.put("success", false);
                response.put("message", "目录不存在");
                return ResponseEntity.ok(response);
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            List<Map<String, Object>> items = new ArrayList<>();

            try (Stream<Path> stream = Files.list(targetPath)) {
                items = stream.map(p -> {
                    Map<String, Object> item = new HashMap<>();
                    File f = p.toFile();
                    item.put("name", f.getName());
                    item.put("isDirectory", f.isDirectory());
                    item.put("size", f.isDirectory() ? 0 : f.length());
                    item.put("sizeText", f.isDirectory() ? "-" : formatFileSize(f.length()));
                    item.put("lastModified", sdf.format(new Date(f.lastModified())));
                    item.put("lastModifiedTime", f.lastModified());
                    item.put("isTextFile", isTextFile(f.getName()));
                    return item;
                })
                .sorted((a, b) -> {
                    // 目录排在前面
                    boolean aDir = (Boolean) a.get("isDirectory");
                    boolean bDir = (Boolean) b.get("isDirectory");
                    if (aDir != bDir) return bDir ? 1 : -1;
                    return ((String) a.get("name")).compareToIgnoreCase((String) b.get("name"));
                })
                .collect(Collectors.toList());
            }

            response.put("success", true);
            response.put("data", items);
            response.put("currentPath", path);
            response.put("homeDirectory", homeDir);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取目录内容失败", e);
            response.put("success", false);
            response.put("message", "获取目录内容失败");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 读取文本文件内容
     */
    @GetMapping("/content")
    public ResponseEntity<Map<String, Object>> getFileContent(
            @RequestParam String path,
            @RequestParam(defaultValue = "0") long offset,
            @RequestParam(defaultValue = "102400") int limit) {
        Map<String, Object> response = new HashMap<>();
        try {
            String homeDir = directoryConfig.getHomeDirectory();
            Path basePath = Paths.get(homeDir).normalize();
            Path filePath = basePath.resolve(path).normalize();

            // 安全检查
            if (!filePath.startsWith(basePath)) {
                response.put("success", false);
                response.put("message", "非法路径");
                return ResponseEntity.ok(response);
            }

            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                response.put("success", false);
                response.put("message", "文件不存在");
                return ResponseEntity.ok(response);
            }

            File file = filePath.toFile();
            long fileSize = file.length();

            byte[] bytes;
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                if (offset >= fileSize) {
                    response.put("success", true);
                    response.put("content", "");
                    response.put("offset", fileSize);
                    response.put("fileSize", fileSize);
                    response.put("hasMore", false);
                    return ResponseEntity.ok(response);
                }

                raf.seek(offset);
                int readSize = (int) Math.min(limit, fileSize - offset);
                bytes = new byte[readSize];
                raf.readFully(bytes);
            }

            String content = new String(bytes, StandardCharsets.UTF_8);
            long newOffset = offset + bytes.length;

            response.put("success", true);
            response.put("content", content);
            response.put("offset", newOffset);
            response.put("fileSize", fileSize);
            response.put("hasMore", newOffset < fileSize);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("读取文件内容失败", e);
            response.put("success", false);
            response.put("message", "读取文件失败");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 下载单个文件
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(@RequestParam String path) {
        try {
            String homeDir = directoryConfig.getHomeDirectory();
            Path basePath = Paths.get(homeDir).normalize();
            Path filePath = basePath.resolve(path).normalize();

            if (!filePath.startsWith(basePath)) {
                return ResponseEntity.badRequest().build();
            }

            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(filePath.toFile());
            String fileName = filePath.getFileName().toString();
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString())
                    .replace("+", "%20");

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedFileName)
                    .body(resource);
        } catch (Exception e) {
            logger.error("下载文件失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 批量下载文件（打包为ZIP）
     */
    @PostMapping("/downloadMultiple")
    public ResponseEntity<Resource> downloadMultiple(@RequestBody Map<String, Object> request) {
        File tempFile = null;
        try {
            String homeDir = directoryConfig.getHomeDirectory();
            Path basePath = Paths.get(homeDir).normalize();
            
            @SuppressWarnings("unchecked")
            List<String> paths = (List<String>) request.get("paths");
            if (paths == null || paths.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // 创建临时ZIP文件
            tempFile = File.createTempFile("download_", ".zip");
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempFile))) {
                for (String filePath : paths) {
                    Path fullPath = basePath.resolve(filePath).normalize();
                    if (!fullPath.startsWith(basePath)) continue;
                    
                    if (Files.exists(fullPath)) {
                        if (Files.isDirectory(fullPath)) {
                            addDirectoryToZip(zos, fullPath, fullPath.getFileName().toString());
                        } else {
                            addFileToZip(zos, fullPath, fullPath.getFileName().toString());
                        }
                    }
                }
            }

            Resource resource = new FileSystemResource(tempFile);
            String zipName = "files_" + System.currentTimeMillis() + ".zip";

            // 注意：这里不能删除临时文件，需要在响应完成后删除
            // Spring会在响应完成后自动处理
            final File fileToDelete = tempFile;
            tempFile = null; // 防止finally中删除

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + zipName)
                    .body(resource);
        } catch (Exception e) {
            logger.error("批量下载失败", e);
            return ResponseEntity.internalServerError().build();
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    private void addFileToZip(ZipOutputStream zos, Path file, String entryName) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        Files.copy(file, zos);
        zos.closeEntry();
    }

    private void addDirectoryToZip(ZipOutputStream zos, Path dir, String baseName) throws IOException {
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.forEach(path -> {
                try {
                    String entryName = baseName + "/" + dir.relativize(path).toString().replace("\\", "/");
                    if (Files.isDirectory(path)) {
                        if (!entryName.endsWith("/")) entryName += "/";
                        zos.putNextEntry(new ZipEntry(entryName));
                        zos.closeEntry();
                    } else {
                        addFileToZip(zos, path, entryName);
                    }
                } catch (IOException e) {
                    logger.error("添加文件到ZIP失败: {}", path, e);
                }
            });
        }
    }

    private boolean isTextFile(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) return false;
        String ext = fileName.substring(dotIndex + 1).toLowerCase();
        return TEXT_EXTENSIONS.contains(ext);
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
    }
}
