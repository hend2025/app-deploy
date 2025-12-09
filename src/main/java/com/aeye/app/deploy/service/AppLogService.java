package com.aeye.app.deploy.service;

import com.aeye.app.deploy.mapper.AppLogMapper;
import com.aeye.app.deploy.model.AppLog;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 应用日志查询服务
 */
@Service
public class AppLogService {

    private static final Logger logger = LoggerFactory.getLogger(AppLogService.class);

    @Autowired
    private AppLogMapper appLogMapper;

    @Autowired
    private LogBufferService logBufferService;

    /**
     * 查询应用日志
     */
    public Map<String, Object> queryLogs(String appCode, String version, Date startTime, Date endTime, int limit) {
        Map<String, Object> result = new HashMap<>();
        try {
            LambdaQueryWrapper<AppLog> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(appCode != null && !appCode.isEmpty(), AppLog::getAppCode, appCode)
                   .eq(version != null && !version.isEmpty(), AppLog::getVersion, version)
                   .ge(startTime != null, AppLog::getLogTime, startTime)
                   .le(endTime != null, AppLog::getLogTime, endTime)
                   .orderByDesc(AppLog::getLogTime)
                   .last("LIMIT " + Math.min(limit, 5000));

            List<AppLog> logs = appLogMapper.selectList(wrapper);

            result.put("success", true);
            result.put("logs", logs);
            result.put("count", logs.size());
            result.put("message", "查询成功");
        } catch (Exception e) {
            logger.error("查询日志失败", e);
            result.put("success", false);
            result.put("message", "查询日志失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 查询应用最新日志（从数据库）
     */
    public Map<String, Object> queryLatestLogs(String appCode, int limit) {
        return queryLogs(appCode, null, null, null, limit);
    }

    /**
     * 从缓冲区读取应用日志（实时日志，用于应用管理和构建页面）
     */
    public Map<String, Object> getLogsFromBuffer(String appCode, int limit) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<AppLog> logs = logBufferService.getLogsFromBuffer(appCode, limit);
            result.put("success", true);
            result.put("logs", logs);
            result.put("count", logs.size());
            result.put("bufferSize", logBufferService.getBufferSize());
            result.put("currentSeq", logBufferService.getCurrentSequence());
            result.put("message", "查询成功");
        } catch (Exception e) {
            logger.error("从缓冲区读取日志失败", e);
            result.put("success", false);
            result.put("message", "读取日志失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 增量读取缓冲区日志（只返回 afterSeq 之后的新日志）
     */
    public Map<String, Object> getLogsIncremental(String appCode, long afterSeq, int limit) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<AppLog> logs = logBufferService.getLogsIncremental(appCode, afterSeq, limit);
            long currentSeq = logBufferService.getCurrentSequence();
            result.put("success", true);
            result.put("logs", logs);
            result.put("count", logs.size());
            result.put("currentSeq", currentSeq);
            result.put("bufferSize", logBufferService.getBufferSize());
            result.put("message", "查询成功");
        } catch (Exception e) {
            logger.error("增量读取日志失败", e);
            result.put("success", false);
            result.put("message", "读取日志失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 查询应用日志（分页）
     */
    public Map<String, Object> queryLogsPaged(String appCode, String version, String logLevel,
                                               Date startTime, Date endTime, int page, int pageSize) {
        Map<String, Object> result = new HashMap<>();
        try {
            LambdaQueryWrapper<AppLog> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(appCode != null && !appCode.isEmpty(), AppLog::getAppCode, appCode)
                   .eq(version != null && !version.isEmpty(), AppLog::getVersion, version)
                   .eq(logLevel != null && !logLevel.isEmpty(), AppLog::getLogLevel, logLevel)
                   .ge(startTime != null, AppLog::getLogTime, startTime)
                   .le(endTime != null, AppLog::getLogTime, endTime)
                   .orderByDesc(AppLog::getLogTime);

            // 计算总数
            Long total = appLogMapper.selectCount(wrapper);

            // 分页查询
            int offset = (page - 1) * pageSize;
            wrapper.last("LIMIT " + offset + ", " + pageSize);
            List<AppLog> logs = appLogMapper.selectList(wrapper);

            result.put("success", true);
            result.put("logs", logs);
            result.put("total", total);
            result.put("page", page);
            result.put("pageSize", pageSize);
            result.put("totalPages", (total + pageSize - 1) / pageSize);
            result.put("message", "查询成功");
        } catch (Exception e) {
            logger.error("分页查询日志失败", e);
            result.put("success", false);
            result.put("message", "查询日志失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 获取缓冲区状态
     */
    public Map<String, Object> getBufferStatus() {
        return logBufferService.getBufferStatus();
    }

    /**
     * 手动刷新缓冲区
     */
    public Map<String, Object> flushBuffer() {
        Map<String, Object> result = new HashMap<>();
        try {
            int beforeSize = logBufferService.getBufferSize();
            logBufferService.flushToDatabase();
            int afterSize = logBufferService.getBufferSize();

            result.put("success", true);
            result.put("flushed", beforeSize - afterSize);
            result.put("message", "刷新完成");
        } catch (Exception e) {
            logger.error("刷新缓冲区失败", e);
            result.put("success", false);
            result.put("message", "刷新失败: " + e.getMessage());
        }
        return result;
    }

}
