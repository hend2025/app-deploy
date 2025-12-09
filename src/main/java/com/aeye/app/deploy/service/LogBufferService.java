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
 * - max-size: 缓存最大条数（滚动存储，超过后删除旧的）
 * - flush-size: 每达到多少条就提交到数据库
 * - flush-interval-minutes: 定时提交间隔
 */
@Service
public class LogBufferService implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(LogBufferService.class);

    @Autowired
    private AppLogMapper appLogMapper;

    @Value("${app.log.cache-size:5000}")
    private int maxBufferSize;

    @Value("${app.log.flush-size:500}")
    private int flushSize;

    @Value("${app.log.flush-interval-minutes:5}")
    private int flushIntervalMinutes;

    // 内存缓冲区（滚动存储）
    private final ConcurrentLinkedDeque<AppLog> logBuffer = new ConcurrentLinkedDeque<>();
    
    // 缓冲区大小计数器
    private final AtomicInteger bufferSize = new AtomicInteger(0);
    
    // 待提交计数器（用于判断是否达到flush-size）
    private final AtomicInteger pendingCount = new AtomicInteger(0);
    
    // 全局日志序号（用于增量读取）
    private final AtomicLong logSequence = new AtomicLong(0);
    
    // 刷新锁，防止并发刷新
    private final ReentrantLock flushLock = new ReentrantLock();
    
    // 定时刷新调度器
    private ScheduledExecutorService scheduler;

    @Override
    public void run(String... args) throws Exception {
        logger.info("日志缓冲服务启动，缓存大小: {}，提交阈值: {}，刷新间隔: {} 分钟",
                maxBufferSize, flushSize, flushIntervalMinutes);
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
                flushToDatabase();
            } catch (Exception e) {
                logger.error("定时刷新日志到数据库时发生异常", e);
            }
        }, flushIntervalMinutes, flushIntervalMinutes, TimeUnit.MINUTES);
    }

    /**
     * 添加日志到缓冲区
     */
    public void addLog(String appCode, String version, String logLevel, String logContent, Date logTime) {
        AppLog log = createAppLog(appCode, version, logLevel, logContent, logTime);
        logBuffer.offerLast(log);
        int currentSize = bufferSize.incrementAndGet();
        int pending = pendingCount.incrementAndGet();

        // 滚动存储：超过最大缓存大小时，删除最旧的日志
        while (currentSize > maxBufferSize) {
            AppLog removed = logBuffer.pollFirst();
            if (removed != null) {
                currentSize = bufferSize.decrementAndGet();
            } else {
                break;
            }
        }

        // 检查是否达到提交阈值
        if (pending >= flushSize) {
            flushToDatabase();
        }
    }

    /**
     * 批量添加日志到缓冲区
     */
    public void addLogs(String appCode, String version, List<String> logLines) {
        if (logLines == null || logLines.isEmpty()) {
            return;
        }

        Date now = new Date();
        for (String line : logLines) {
            if (line == null || line.trim().isEmpty()) {
                continue;
            }
            String logLevel = parseLogLevel(line);
            addLog(appCode, version, logLevel, line, now);
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
        log.setCreateTime(new Date());
        log.setSeq(logSequence.incrementAndGet()); // 设置序号
        return log;
    }

    /**
     * 解析日志级别
     */
    private String parseLogLevel(String logContent) {
        if (logContent == null) {
            return "INFO";
        }
        String upper = logContent.toUpperCase();
        if (upper.contains("ERROR")) {
            return "ERROR";
        } else if (upper.contains("WARN")) {
            return "WARN";
        } else if (upper.contains("DEBUG")) {
            return "DEBUG";
        } else if (upper.contains("TRACE")) {
            return "TRACE";
        }
        return "INFO";
    }

    /**
     * 刷新缓冲区到数据库（只提交待提交的日志，不清空缓冲区）
     */
    public void flushToDatabase() {
        if (!flushLock.tryLock()) {
            return;
        }

        try {
            int pending = pendingCount.get();
            if (pending == 0) {
                return;
            }

            logger.info("开始刷新日志到数据库，待提交条数: {}", pending);

            // 从缓冲区复制待提交的日志（不删除，保留在缓冲区供查询）
            List<AppLog> logsToFlush = new ArrayList<>();
            int count = 0;
            
            // 从尾部向前取最新的pending条日志
            Iterator<AppLog> it = logBuffer.descendingIterator();
            while (it.hasNext() && count < pending) {
                logsToFlush.add(it.next());
                count++;
            }
            
            // 反转顺序，保持时间顺序
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

            // 重置待提交计数
            pendingCount.set(0);
            logger.info("日志刷新完成，已提交 {} 条", logsToFlush.size());

        } finally {
            flushLock.unlock();
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
     * 获取当前缓冲区大小
     */
    public int getBufferSize() {
        return bufferSize.get();
    }

    /**
     * 从缓冲区读取指定应用的日志（不会清除缓冲区）
     */
    public List<AppLog> getLogsFromBuffer(String appCode, int limit) {
        List<AppLog> result = new ArrayList<>();
        int count = 0;
        
        for (AppLog log : logBuffer) {
            if (appCode == null || appCode.equals(log.getAppCode())) {
                result.add(log);
                count++;
                if (limit > 0 && count >= limit) {
                    break;
                }
            }
        }
        
        return result;
    }

    /**
     * 增量读取日志（从指定序号之后的日志）
     * @param appCode 应用编码（可选，为空则读取所有应用）
     * @param afterSeq 上次读取的最后序号，返回大于此序号的日志
     * @param limit 最大返回条数
     * @return 增量日志列表
     */
    public List<AppLog> getLogsIncremental(String appCode, long afterSeq, int limit) {
        List<AppLog> result = new ArrayList<>();
        int count = 0;
        
        for (AppLog log : logBuffer) {
            if (log.getSeq() != null && log.getSeq() > afterSeq) {
                if (appCode == null || appCode.isEmpty() || appCode.equals(log.getAppCode())) {
                    result.add(log);
                    count++;
                    if (limit > 0 && count >= limit) {
                        break;
                    }
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
     * 获取缓冲区状态
     */
    public Map<String, Object> getBufferStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("currentSize", bufferSize.get());
        status.put("maxSize", maxBufferSize);
        status.put("flushSize", flushSize);
        status.put("pendingCount", pendingCount.get());
        status.put("flushIntervalMinutes", flushIntervalMinutes);
        return status;
    }

    @PreDestroy
    public void shutdown() {
        logger.info("正在关闭日志缓冲服务...");
        
        flushToDatabase();
        
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
