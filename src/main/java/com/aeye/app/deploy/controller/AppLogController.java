package com.aeye.app.deploy.controller;

import com.aeye.app.deploy.service.AppLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/logs")
public class AppLogController {

    @Autowired
    private AppLogService appLogService;

    @Value("${app.log.cache-size:5000}")
    private int cacheSize;

    @GetMapping("/buffer/incremental")
    public ResponseEntity<Map<String, Object>> getLogsIncremental(
            @RequestParam(required = false) String appCode,
            @RequestParam(defaultValue = "0") long afterSeq,
            @RequestParam(defaultValue = "1000") int limit) {
        Map<String, Object> result = appLogService.getLogsIncremental(appCode, afterSeq, limit);
        result.put("timestamp", LocalDateTime.now());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getLogConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("cacheSize", cacheSize);
        return ResponseEntity.ok(config);
    }

}
