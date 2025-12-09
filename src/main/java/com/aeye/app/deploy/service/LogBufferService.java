package com.aeye.app.deploy.service;

import com.aeye.app.deploy.mapper.AppLogMapper;
import com.aeye.app.deploy.model.AppLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 日志内存缓冲服务
 * <p>
 * 提供高性能的日志缓冲机制，支持：
 * <ul>
 *   <li>按应用隔离的独立缓冲区</li>
 *   <li>滚动存储（超过最大条数自动删除旧日志）</li>
 *   <li>增量读取（通过序号实现客户端增量拉取）</li>
 *   <li>定时/阈值触发的数据库持久化</li>
 * </ul>
 * 
 * 配置参数：
 * <ul>
 *   <li>app.log.cache-size: 每个应用的缓存最大条数（默认5000）</li>
 *   <li>app.log.flush-size: 触发持久化的阈值（默认500）</li>
 *   <li>app.log.flush-interval-minutes: 定时持久化间隔（默认5分钟）</li>
 * </ul>
 *
 * @author aeye
 * @since 1.0.0
 */
@Service
public class LogBufferService implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(LogBufferService.class);

    @Autowired
    private AppLogMapper appLogMapper;

    @Value("${app.log.cache-size:5000}")
    private int maxBufferSizePerApp;

    @Value("${app.log.flush-size:500}")
    private int flushSize;

    @Value("${app.log.flush-interval-minutes:5}")
    private int flushIntervalMinutes;

    /**
     * 应用日志缓冲区内部类
     * <p>
     * 每个应用独立的缓冲区，包含日志队列、计数器和刷新锁
     */
    private static class AppLogBuffer {
        /** 日志双端队列（支持头尾操作） */
        final ConcurrentLinkedDeque<AppLog> logs = new ConcurrentLinkedDeque<>();
        /** 当前缓冲区大小 */
        final AtomicInteger size = new AtomicInteger(0);
        /** 待持久化的日志数量 */
        final AtomicInteger pendingCount = new AtomicInteger(0);
        /** 刷新锁（防止并发刷新） */
        final ReentrantLock flushLock = new ReentrantLock();
    }

    /** 按应用隔离的缓冲区映射 */
    private final ConcurrentHashMap<String, AppLogBuffer> appBuffers = new ConcurrentHashMap<>();
    
    /** 全局日志序号生成器（用于增量读取） */
    private final AtomicLong logSequence = new AtomicLong(0);
    
    /** 定时刷新调度器 */
    private ScheduledExecutorService scheduler;

    /**
     * 获取或创建应用的日志缓冲区
     */
    private AppLogBuffer getOrCreateBuffer(String appCode) {
        return appBuffers.computeIfAbsent(appCode, k -> new AppLogBuffer());
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("日志缓冲服务启动，每应用缓存大小: {}，提交阈值: {}，刷新间隔: {} 分钟",
                maxBufferSizePerApp, flushSize, flushIntervalMinutes);
        startFlushScheduler();
    }

    /**
     * 启动定时刷新调度器
     */
    private void startFlushScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "log-buffer-flush");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(() -> {
            try {
                flushAllToDatabase();
            } catch (Exception e) {
                logger.error("定时刷新日志到数据库时发生异常", e);
            }
        }, flushIntervalMinutes, flushIntervalMinutes, TimeUnit.MINUTES);
    }

    /**
     * 添加日志到缓冲区
     * <p>
     * 日志会被添加到对应应用的独立缓冲区，超过最大容量时自动删除旧日志。
     * 当待持久化日志数达到阈值时，自动触发数据库写入。
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
        int pending = buffer.pendingCount.incrementAndGet();

        // 滚动存储：超过该应用的最大缓存大小时，删除最旧的日志
        while (currentSize > maxBufferSizePerApp) {
            AppLog removed = buffer.logs.pollFirst();
            if (removed != null) {
                currentSize = buffer.size.decrementAndGet();
            } else {
                break;
            }
        }

        // 检查是否达到提交阈值
        if (pending >= flushSize) {
            flushToDatabase(appCode);
        }
    }

    /**
     * 创建AppLog对象
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
     * 刷新指定应用的缓冲区到数据库
     * <p>
     * 将待持久化的日志批量写入数据库，使用锁防止并发刷新
     *
     * @param appCode 应用编码
     */
    public void flushToDatabase(String appCode) {
        AppLogBuffer buffer = appBuffers.get(appCode);
        if (buffer == null) {
            return;
        }

        if (!buffer.flushLock.tryLock()) {
            return;
        }

        try {
            int pending = buffer.pendingCount.get();
            if (pending == 0) {
                return;
            }

            logger.info("开始刷新应用[{}]日志到数据库，待提交条数: {}", appCode, pending);

            // 从缓冲区复制待提交的日志
            List<AppLog> logsToFlush = new ArrayList<>();
            int count = 0;
            
            Iterator<AppLog> it = buffer.logs.descendingIterator();
            while (it.hasNext() && count < pending) {
                logsToFlush.add(it.next());
                count++;
            }
            
            Collections.reverse(logsToFlush);

            // 批量插入
            if (!logsToFlush.isEmpty()) {
                int batchSize = 500;
                for (int i = 0; i < logsToFlush.size(); i += batchSize) {
                    int end = Math.min(i + batchSize, logsToFlush.size());
                    List<AppLog> batch = logsToFlush.subList(i, end);
                    batchInsertLogs(batch);
                }
            }

            buffer.pendingCount.set(0);
            logger.info("应用[{}]日志刷新完成，已提交 {} 条", appCode, logsToFlush.size());

        } finally {
            buffer.flushLock.unlock();
        }
    }

    /**
     * 刷新所有应用的缓冲区到数据库
     */
    public void flushAllToDatabase() {
        for (String appCode : appBuffers.keySet()) {
            flushToDatabase(appCode);
        }
    }

    /**
     * 批量插入日志
     */
    private void batchInsertLogs(List<AppLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }
        try {
            appLogMapper.batchInsert(logs);
            logger.debug("批量插入 {} 条日志", logs.size());
        } catch (Exception e) {
            logger.error("批量插入日志失败，尝试逐条插入", e);
            for (AppLog log : logs) {
                try {
                    appLogMapper.insert(log);
                } catch (Exception ex) {
                    logger.error("插入单条日志失败: {}", log.getLogContent(), ex);
                }
            }
        }
    }

    /**
     * 获取所有应用的缓冲区总大小
     */
    public int getTotalBufferSize() {
        return appBuffers.values().stream().mapToInt(b -> b.size.get()).sum();
    }

    /**
     * 兼容旧接口
     */
    public int getBufferSize() {
        return getTotalBufferSize();
    }

    /**
     * 增量读取日志
     * <p>
     * 返回指定序号之后的新日志，用于客户端轮询获取增量数据
     *
     * @param appCode  应用编码
     * @param afterSeq 上次读取的最后序号，返回大于此序号的日志
     * @param limit    最大返回条数
     * @return 增量日志列表（按序号升序）
     */
    public List<AppLog> getLogsIncremental(String appCode, long afterSeq, int limit) {
        List<AppLog> result = new ArrayList<>();

        AppLogBuffer buffer = appBuffers.get(appCode);
        if (buffer == null) {
            return result;
        }
        
        for (AppLog log : buffer.logs) {
            if (log.getSeq() != null && log.getSeq() > afterSeq) {
                result.add(log);
                if (limit > 0 && result.size() >= limit) {
                    break;
                }
            }
        }
        return result;
    }

    /**
     * 获取当前最大序号
     */
    public long getCurrentSequence() {
        return logSequence.get();
    }

    /**
     * 清除指定应用的缓冲区
     * <p>
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

    @PreDestroy
    public void shutdown() {
        logger.info("正在关闭日志缓冲服务...");
        
        flushAllToDatabase();
        
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.warn("日志缓冲调度器未能在10秒内关闭，强制关闭");
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.error("日志缓冲调度器关闭时被中断", e);
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        logger.info("日志缓冲服务已关闭");
    }

}
