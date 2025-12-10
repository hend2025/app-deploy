package com.aeye.app.deploy.controller;

import com.aeye.app.deploy.service.AppLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/logs")
public class AppLogController {

    @Autowired
    private AppLogService appLogService;

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
