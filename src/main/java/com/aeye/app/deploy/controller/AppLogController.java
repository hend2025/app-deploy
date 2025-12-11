package com.aeye.app.deploy.controller;

import com.aeye.app.deploy.service.AppLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 应用日志控制器
 * 
 * 提供日志缓冲区的增量读取接口，用于前端实时日志展示
 *
 * @author aeye
 * @since 1.0.0
 */
@RestController
@RequestMapping("/logs")
public class AppLogController {

    @Autowired
    private AppLogService appLogService;

    /** 每应用日志缓存大小 */
    @Value("${app.log.cache-size:5000}")
    private int cacheSize;

    /**
     * 增量获取日志
     * 根据序号获取指定应用的新增日志，用于前端轮询
     *
     * @param appCode  应用编码
     * @param afterSeq 起始序号（返回大于此序号的日志）
     * @param limit    返回数量限制
     * @return 日志列表和最新序号
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
     * 获取日志配置
     *
     * @return 日志配置信息
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getLogConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("cacheSize", cacheSize);
        return ResponseEntity.ok(config);
    }

}
