package com.aeye.app.deploy.controller;

import com.aeye.app.deploy.service.FileReadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/logs")
public class LogFileController {

    @Autowired
    private FileReadService fileReadService;

    @Value("${app.directory.logs:/home/logs}")
    private String logsDir;

    /**
     * 获取日志文件列表
     */
    @GetMapping("/file/log-files")
    public ResponseEntity<Map<String, Object>> getLogFiles() {
        Map<String, Object> result = fileReadService.getLogFiles();
        result.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(result);
    }

    /**
     * 读取文件最后N行
     */
    @GetMapping("/file/read-file-last-lines")
    public ResponseEntity<Map<String, Object>> readFileLastLines(
            @RequestParam String fileName,
            @RequestParam(defaultValue = "1000") int lastLines) {
        Map<String, Object> result = fileReadService.readFileLastLines(fileName, lastLines);
        result.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(result);
    }

    /**
     * 增量读取文件内容
     */
    @GetMapping("/file/read-file-incremental")
    public ResponseEntity<Map<String, Object>> readFileIncremental(
            @RequestParam String fileName,
            @RequestParam int fromLine) {
        Map<String, Object> result = fileReadService.readFileIncremental(fileName, fromLine);
        result.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(result);
    }

    /**
     * 下载文件
     */
    @GetMapping("/file/download-file")
    public ResponseEntity<?> downloadFile(@RequestParam String fileName) {
        Map<String, Object> result = fileReadService.readFile(fileName);
        if ((Boolean) result.get("success")) {
            String content = (String) result.get("content");
            String fileNameForDownload = (String) result.get("fileName");
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + fileNameForDownload + "\"")
                    .header("Content-Type", "text/plain; charset=utf-8")
                    .body(content);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 浏览目录内容
     */
    @GetMapping("/file/browse")
    public ResponseEntity<Map<String, Object>> browseDirectory(@RequestParam(defaultValue = "") String path) {
        Map<String, Object> result = new HashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        
        try {
            // 如果path为空，使用配置的日志目录
            String targetPath = path.isEmpty() ? logsDir : path;
            File dir = new File(targetPath);
            
            if (!dir.exists() || !dir.isDirectory()) {
                result.put("success", false);
                result.put("message", "目录不存在: " + targetPath);
                return ResponseEntity.ok(result);
            }
            
            // 获取目录内容
            File[] files = dir.listFiles();
            List<Map<String, Object>> items = new ArrayList<>();
            int fileCount = 0;
            int folderCount = 0;
            long totalSize = 0;
            
            if (files != null) {
                // 排序：文件夹在前，文件在后，按名称排序
                Arrays.sort(files, (a, b) -> {
                    if (a.isDirectory() && !b.isDirectory()) return -1;
                    if (!a.isDirectory() && b.isDirectory()) return 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                });
                
                for (File file : files) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("name", file.getName());
                    item.put("path", file.getAbsolutePath());
                    item.put("isDirectory", file.isDirectory());
                    item.put("size", file.length());
                    item.put("lastModified", formatter.format(
                        Instant.ofEpochMilli(file.lastModified()).atZone(ZoneId.systemDefault()).toLocalDateTime()));
                    items.add(item);
                    
                    if (file.isDirectory()) {
                        folderCount++;
                    } else {
                        fileCount++;
                        totalSize += file.length();
                    }
                }
            }
            
            result.put("success", true);
            result.put("currentPath", dir.getAbsolutePath());
            result.put("parentPath", dir.getParent());
            result.put("items", items);
            result.put("fileCount", fileCount);
            result.put("folderCount", folderCount);
            result.put("totalSize", formatFileSize(totalSize));
            result.put("timestamp", LocalDateTime.now());
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "浏览目录失败: " + e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
    
    private String formatFileSize(long size) {
        if (size < 1024) return size + "B";
        if (size < 1024 * 1024) return String.format("%.1fKB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1fMB", size / (1024.0 * 1024));
        return String.format("%.1fGB", size / (1024.0 * 1024 * 1024));
    }

}