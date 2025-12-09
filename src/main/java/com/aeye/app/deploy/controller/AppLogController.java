package com.aeye.app.deploy.controller;

import com.aeye.app.deploy.service.AppLogService;
import com.aeye.app.deploy.service.LogBufferService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 应用日志控制器
 */
@RestController
@RequestMapping("/logs/db")
public class AppLogController {

    @Autowired
    private AppLogService appLogService;

    @Autowired
    private LogBufferService logBufferService;

    /**
     * 查询应用日志
     */
    @GetMapping("/query")
    public ResponseEntity<Map<String, Object>> queryLogs(
            @RequestParam(required = false) String appCode,
            @RequestParam(required = false) String version,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime,
            @RequestParam(defaultValue = "1000") int limit) {
        Map<String, Object> result = appLogService.queryLogs(appCode, version, startTime, endTime, limit);
        result.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(result);
    }

    /**
     * 查询应用最新日志（从数据库）
     */
    @GetMapping("/latest")
    public ResponseEntity<Map<String, Object>> queryLatestLogs(
            @RequestParam String appCode,
            @RequestParam(defaultValue = "500") int limit) {
        Map<String, Object> result = appLogService.queryLatestLogs(appCode, limit);
        result.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(result);
    }

    /**
     * 从缓冲区读取实时日志（用于应用管理和构建页面）
     */
    @GetMapping("/buffer/logs")
    public ResponseEntity<Map<String, Object>> getLogsFromBuffer(
            @RequestParam String appCode,
            @RequestParam(defaultValue = "1000") int limit) {
        Map<String, Object> result = appLogService.getLogsFromBuffer(appCode, limit);
        result.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(result);
    }

    /**
     * 增量读取缓冲区日志（只返回 afterSeq 之后的新日志）
     * @param appCode 应用编码
     * @param afterSeq 上次读取的最后序号，首次传0
     * @param limit 最大返回条数
     */
    @GetMapping("/buffer/incremental")
    public ResponseEntity<Map<String, Object>> getLogsIncremental(
            @RequestParam(required = false) String appCode,
            @RequestParam(defaultValue = "0") long afterSeq,
            @RequestParam(defaultValue = "1000") int limit) {
        Map<String, Object> result = appLogService.getLogsIncremental(appCode, afterSeq, limit);
        result.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(result);
    }

    /**
     * 分页查询日志
     */
    @GetMapping("/page")
    public ResponseEntity<Map<String, Object>> queryLogsPaged(
            @RequestParam(required = false) String appCode,
            @RequestParam(required = false) String version,
            @RequestParam(required = false) String logLevel,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        Map<String, Object> result = appLogService.queryLogsPaged(appCode, version, logLevel, startTime, endTime, page, pageSize);
        result.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(result);
    }

    /**
     * 获取缓冲区状态
     */
    @GetMapping("/buffer/status")
    public ResponseEntity<Map<String, Object>> getBufferStatus() {
        Map<String, Object> result = appLogService.getBufferStatus();
        result.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(result);
    }

    /**
     * 手动刷新缓冲区
     */
    @PostMapping("/buffer/flush")
    public ResponseEntity<Map<String, Object>> flushBuffer() {
        Map<String, Object> result = appLogService.flushBuffer();
        result.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(result);
    }

    /**
     * 添加日志（供内部调用或测试）
     */
    @PostMapping("/add")
    public ResponseEntity<Map<String, Object>> addLog(
            @RequestParam String appCode,
            @RequestParam(required = false) String version,
            @RequestParam(required = false, defaultValue = "INFO") String logLevel,
            @RequestParam String logContent) {
        logBufferService.addLog(appCode, version, logLevel, logContent, new Date());
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "日志已添加到缓冲区");
        result.put("bufferSize", logBufferService.getBufferSize());
        result.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(result);
    }
}
