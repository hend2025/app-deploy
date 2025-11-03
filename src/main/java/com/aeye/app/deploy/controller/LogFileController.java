package com.aeye.app.deploy.controller;

import com.aeye.app.deploy.service.FileReadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.Map;

@Controller
@RequestMapping("/logs")
public class LogFileController {

    @Autowired
    private FileReadService fileReadService;

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

}