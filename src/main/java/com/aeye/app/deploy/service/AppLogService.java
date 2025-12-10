package com.aeye.app.deploy.service;

import com.aeye.app.deploy.model.AppLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 应用日志服务
 */
@Service
public class AppLogService {

    private static final Logger logger = LoggerFactory.getLogger(AppLogService.class);

    @Autowired
    private LogBufferService logBufferService;

    /**
     * 增量读取缓冲区日志（只返回 afterSeq 之后的新日志）
     */
    public Map<String, Object> getLogsIncremental(String appCode, long afterSeq, int limit) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<AppLog> logs = logBufferService.getLogsIncremental(appCode, afterSeq, limit);
            long currentSeq = 0;
            if (!logs.isEmpty()) {
                currentSeq = logs.stream().mapToLong(AppLog::getSeq).max().orElse(currentSeq);
            }
            result.put("success", true);
            result.put("logs", logs);
            result.put("count", logs.size());
            result.put("currentSeq", currentSeq);
            result.put("message", "查询成功");
        } catch (Exception e) {
            logger.error("增量读取日志失败", e);
            result.put("success", false);
            result.put("message", "读取日志失败: " + e.getMessage());
        }
        return result;
    }

}
