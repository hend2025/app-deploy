package com.aeye.app.deploy.controller;

import com.aeye.app.deploy.service.AppLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 应用日志控制器
 */
@RestController
@RequestMapping("/logs")
public class AppLogController {

    @Autowired
    private AppLogService appLogService;

    /**
     * 增量读取缓冲区日志（只返回 afterSeq 之后的新日志）
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

}
