package com.aeye.app.deploy.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 日志文件管理控制器
 * <p>
 * 提供日志文件的浏览、查看和下载功能
 */
@RestController
@RequestMapping("/logFiles")
public class LogFileController {

    private static final Logger logger = LoggerFactory.getLogger(LogFileController.class);

    @Value("${app.directory.logs:/home/logs}")
    private String logsDir;

    /**
     * 获取应用列表（日志目录下的子目录）
     */
    @GetMapping("/apps")
    public ResponseEntity<Map<String, Object>> getAppList() {
        Map<String, Object> response = new HashMap<>();
        try {
            File logPath = new File(logsDir);
            if (!logPath.exists() || !logPath.isDirectory()) {
                response.put("success", true);
                response.put("data", Collections.emptyList());
                return ResponseEntity.ok(response);
            }

            File[] appDirs = logPath.listFiles(File::isDirectory);
            List<Map<String, Object>> apps = new ArrayList<>();
            
            if (appDirs != null) {
                for (File dir : appDirs) {
                    Map<String, Object> app = new HashMap<>();
                    app.put("appCode", dir.getName());
                    app.put("fileCount", countLogFiles(dir));
                    apps.add(app);
                }
                // 按名称排序
                apps.sort((a, b) -> ((String) a.get("appCode")).compareTo((String) b.get("appCode")));
            }

            response.put("success", true);
            response.put("data", apps);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取应用列表失败", e);
            response.put("success", false);
            response.put("message", "获取应用列表失败");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取指定应用的日志文件列表
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getFileList(@RequestParam String appCode) {
        Map<String, Object> response = new HashMap<>();
        try {
            // 验证appCode防止路径遍历
            if (!isValidAppCode(appCode)) {
                response.put("success", false);
                response.put("message", "无效的应用编码");
                return ResponseEntity.ok(response);
            }

            Path appLogPath = Paths.get(logsDir, appCode);
            if (!Files.exists(appLogPath) || !Files.isDirectory(appLogPath)) {
                response.put("success", true);
                response.put("data", Collections.emptyList());
                return ResponseEntity.ok(response);
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            List<Map<String, Object>> files = new ArrayList<>();

            try (Stream<Path> stream = Files.list(appLogPath)) {
                files = stream
                    .filter(p -> p.toString().endsWith(".log"))
                    .map(p -> {
                        Map<String, Object> file = new HashMap<>();
                        File f = p.toFile();
                        file.put("fileName", f.getName());
                        file.put("size", f.length());
                        file.put("sizeText", formatFileSize(f.length()));
                        file.put("lastModified", sdf.format(new Date(f.lastModified())));
                        file.put("lastModifiedTime", f.lastModified());
                        return file;
                    })
                    .sorted((a, b) -> Long.compare(
                        (Long) b.get("lastModifiedTime"), 
                        (Long) a.get("lastModifiedTime")))
                    .collect(Collectors.toList());
            }

            response.put("success", true);
            response.put("data", files);
            response.put("total", files.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取日志文件列表失败", e);
            response.put("success", false);
            response.put("message", "获取文件列表失败");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 读取日志文件内容
     */
    @GetMapping("/content")
    public ResponseEntity<Map<String, Object>> getFileContent(
            @RequestParam String appCode,
            @RequestParam String fileName,
            @RequestParam(defaultValue = "0") long offset,
            @RequestParam(defaultValue = "102400") int limit) {
        Map<String, Object> response = new HashMap<>();
        try {
            // 验证参数
            if (!isValidAppCode(appCode) || !isValidFileName(fileName)) {
                response.put("success", false);
                response.put("message", "无效的参数");
                return ResponseEntity.ok(response);
            }

            Path filePath = Paths.get(logsDir, appCode, fileName);
            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                response.put("success", false);
                response.put("message", "文件不存在");
                return ResponseEntity.ok(response);
            }

            File file = filePath.toFile();
            long fileSize = file.length();

            // 读取文件内容
            byte[] bytes;
            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
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
            logger.error("读取日志文件失败", e);
            response.put("success", false);
            response.put("message", "读取文件失败");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 下载日志文件
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(
            @RequestParam String appCode,
            @RequestParam String fileName) {
        try {
            // 验证参数
            if (!isValidAppCode(appCode) || !isValidFileName(fileName)) {
                return ResponseEntity.badRequest().build();
            }

            Path filePath = Paths.get(logsDir, appCode, fileName);
            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(filePath.toFile());
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString())
                    .replace("+", "%20");

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename*=UTF-8''" + encodedFileName)
                    .body(resource);
        } catch (Exception e) {
            logger.error("下载日志文件失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 删除日志文件
     */
    @PostMapping("/delete")
    public ResponseEntity<Map<String, Object>> deleteFile(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            String appCode = request.get("appCode");
            String fileName = request.get("fileName");

            if (!isValidAppCode(appCode) || !isValidFileName(fileName)) {
                response.put("success", false);
                response.put("message", "无效的参数");
                return ResponseEntity.ok(response);
            }

            Path filePath = Paths.get(logsDir, appCode, fileName);
            if (!Files.exists(filePath)) {
                response.put("success", false);
                response.put("message", "文件不存在");
                return ResponseEntity.ok(response);
            }

            Files.delete(filePath);
            response.put("success", true);
            response.put("message", "删除成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("删除日志文件失败", e);
            response.put("success", false);
            response.put("message", "删除失败");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    private boolean isValidAppCode(String appCode) {
        return appCode != null && appCode.matches("^[a-zA-Z0-9_\\-]+$");
    }

    private boolean isValidFileName(String fileName) {
        return fileName != null && fileName.matches("^[a-zA-Z0-9_\\-.]+\\.log$");
    }

    private int countLogFiles(File dir) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".log"));
        return files != null ? files.length : 0;
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
    }
}
