package com.aeye.app.deploy.service;

import com.aeye.app.deploy.model.AppLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 日志缓冲服务
 * 
 * 提供应用日志的内存缓冲功能，支持：
 * - 按应用隔离的日志缓冲区
 * - 滚动存储（超过最大缓存大小时自动删除旧日志）
 * - 增量读取（基于序号）
 * - 异步写入文件和WebSocket推送
 *
 * @author aeye
 * @since 1.0.0
 */
@Service
public class LogBufferService implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(LogBufferService.class);

    @Autowired
    private LogFileWriterService logFileWriterService;

    @Autowired
    private LogWebSocketHandler logWebSocketHandler;

    @Value("${app.log.cache-size:5000}")
    private int maxBufferSizePerApp;

    /**
     * 应用日志缓冲区内部类
     */
    private static class AppLogBuffer {
        /** 日志双端队列（支持头尾操作） */
        final ConcurrentLinkedDeque<AppLog> logs = new ConcurrentLinkedDeque<>();
        /** 当前缓冲区大小 */
        final AtomicInteger size = new AtomicInteger(0);
    }

    /** 按应用隔离的缓冲区映射 */
    private final ConcurrentHashMap<String, AppLogBuffer> appBuffers = new ConcurrentHashMap<>();

    /** 全局日志序号生成器（用于增量读取） */
    private final AtomicLong logSequence = new AtomicLong(0);

    /**
     * 获取或创建应用的日志缓冲区
     */
    private AppLogBuffer getOrCreateBuffer(String appCode) {
        return appBuffers.computeIfAbsent(appCode, k -> new AppLogBuffer());
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("日志缓冲服务启动，每应用缓存大小: {}", maxBufferSizePerApp);
    }

    /**
     * 添加日志到缓冲区
     * 同时触发文件写入和WebSocket推送
     *
     * @param appCode    应用编码
     * @param version    版本号
     * @param logLevel   日志级别
     * @param logContent 日志内容
     * @param logTime    日志时间
     */
    public void addLog(String appCode, String version, String logLevel, String logContent, Date logTime) {
        AppLogBuffer buffer = getOrCreateBuffer(appCode);
        AppLog log = createAppLog(appCode, version, logLevel, logContent, logTime);
        
        buffer.logs.offerLast(log);
        int currentSize = buffer.size.incrementAndGet();

        // 滚动存储：超过该应用的最大缓存大小时，删除最旧的日志
        while (currentSize > maxBufferSizePerApp) {
            AppLog removed = buffer.logs.pollFirst();
            if (removed != null) {
                currentSize = buffer.size.decrementAndGet();
            } else {
                break;
            }
        }

        // 异步写入日志文件
        logFileWriterService.addLog(appCode, version, logLevel, logContent, logTime);

        // WebSocket推送日志
        logWebSocketHandler.pushLog(log);
    }

    /**
     * 创建日志对象
     * 自动分配全局递增序号
     */
    private AppLog createAppLog(String appCode, String version, String logLevel, String logContent, Date logTime) {
        AppLog log = new AppLog();
        log.setAppCode(appCode);
        log.setVersion(version);
        log.setLogLevel(logLevel);
        log.setLogContent(logContent);
        log.setLogTime(logTime != null ? logTime : new Date());
        log.setSeq(logSequence.incrementAndGet());
        return log;
    }

    /**
     * 增量获取日志
     * 返回序号大于afterSeq的日志列表
     *
     * @param appCode  应用编码
     * @param afterSeq 起始序号
     * @param limit    返回数量限制
     * @return 日志列表
     */
    public List<AppLog> getLogsIncremental(String appCode, long afterSeq, int limit) {
        List<AppLog> result = new ArrayList<>();

        AppLogBuffer buffer = appBuffers.get(appCode);
        if (buffer == null) {
            return result;
        }
        
        // 优化：从尾部向前查找起始位置，减少遍历次数
        // 因为日志是按seq递增的，可以利用这个特性
        Iterator<AppLog> iterator = buffer.logs.iterator();
        
        // 如果afterSeq为0，直接从头开始
        if (afterSeq == 0) {
            while (iterator.hasNext() && (limit <= 0 || result.size() < limit)) {
                result.add(iterator.next());
            }
        } else {
            // 跳过seq <= afterSeq的日志
            while (iterator.hasNext()) {
                AppLog log = iterator.next();
                if (log.getSeq() != null && log.getSeq() > afterSeq) {
                    result.add(log);
                    if (limit > 0 && result.size() >= limit) {
                        break;
                    }
                    // 找到第一个符合条件的后，后续都符合条件
                    while (iterator.hasNext() && (limit <= 0 || result.size() < limit)) {
                        result.add(iterator.next());
                    }
                    break;
                }
            }
        }
        return result;
    }

    /**
     * 清除指定应用的缓冲区
     * 用于构建开始前清除旧日志，避免显示上次构建的日志
     *
     * @param appCode 应用编码
     */
    public void clearBuffer(String appCode) {
        AppLogBuffer buffer = appBuffers.remove(appCode);
        if (buffer != null) {
            logger.info("已清除应用[{}]的日志缓冲区", appCode);
        }
    }

    /**
     * 开始新的日志会话（用于构建或运行开始时）
     * 清除内存缓冲区并通知文件写入服务开始新会话，递增运行/打包次数
     *
     * @param appCode 应用编码
     * @param version 版本号
     */
    public void startNewSession(String appCode, String version) {
        // 清除内存缓冲区
        clearBuffer(appCode);
        // 通知文件写入服务开始新会话
        logFileWriterService.startNewSession(appCode, version);
    }

}
