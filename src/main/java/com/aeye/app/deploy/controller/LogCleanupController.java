package com.aeye.app.deploy.controller;

import com.aeye.app.deploy.service.LogCleanupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/logs/cleanup")
public class LogCleanupController {

    @Autowired
    private LogCleanupService logCleanupService;

    /**
     * 手动触发日志清理
     */
    @PostMapping("/manual")
    public ResponseEntity<Map<String, Object>> manualCleanup() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            logCleanupService.manualCleanup();
            result.put("success", true);
            result.put("message", "日志清理任务已触发");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "触发日志清理失败: " + e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }

    /**
     * 获取日志目录信息
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getLogDirectoryInfo() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String info = logCleanupService.getLogDirectoryInfo();
            result.put("success", true);
            result.put("info", info);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "获取日志目录信息失败: " + e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
}
