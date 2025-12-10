package com.aeye.app.deploy.service;

import com.aeye.app.deploy.model.AppLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class LogFileWriterService implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(LogFileWriterService.class);

    @Value("${app.directory.logs:/home/logs}")
    private String logsDir;

    /** 单个日志文件最大大小（MB），默认20MB */
    @Value("${app.log.max-file-size-mb:20}")
    private int maxFileSizeMb;

    @Value("${app.log.flush-size:500}")
    private int flushSize;

    @Value("${app.log.flush-interval-minutes:5}")
    private int flushIntervalMinutes;

    /**
     * 日志缓冲区内部类
     * <p>
     * 每个应用+日志类型+版本组合独立的缓冲区
     */
    private static class LogFileBuffer {
        /** 日志队列 */
        final ConcurrentLinkedDeque<AppLog> logs = new ConcurrentLinkedDeque<>();
        /** 待写入的日志数量 */
        final AtomicInteger pendingCount = new AtomicInteger(0);
        /** 写入锁（防止并发写入） */
        final ReentrantLock writeLock = new ReentrantLock();
        /** 当前日志文件 */
        volatile File currentFile;
        /** 当前文件大小 */
        volatile long currentFileSize = 0;
        /** 当前版本 */
        volatile String currentVersion;
        /** 当前运行/打包次数（x） */
        volatile int runCount = 0;
        /** 当前文件序号（y） */
        volatile int fileSeq = 1;
    }

    /** 缓冲区映射：key = appCode */
    private final ConcurrentHashMap<String, LogFileBuffer> buffers = new ConcurrentHashMap<>();

    /** 定时刷新调度器 */
    private ScheduledExecutorService scheduler;

    /** 异步写入线程池 */
    private ExecutorService writerExecutor;

    /** 日志时间格式化器（使用ThreadLocal保证线程安全） */
    private static final ThreadLocal<SimpleDateFormat> logTimeFormat = 
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS"));

    /**
     * 获取或创建缓冲区
     */
    private LogFileBuffer getOrCreateBuffer(String appCode) {
        return buffers.computeIfAbsent(appCode, k -> new LogFileBuffer());
    }

    /**
     * 获取最大文件大小（字节）
     */
    private long getMaxFileSize() {
        return (long) maxFileSizeMb * 1024 * 1024;
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("日志文件写入服务启动，日志目录: {}，刷新阈值: {}，刷新间隔: {} 分钟，单文件最大: {}MB",
                logsDir, flushSize, flushIntervalMinutes, maxFileSizeMb);
        
        // 确保日志根目录存在
        Path logPath = Paths.get(logsDir);
        if (!Files.exists(logPath)) {
            Files.createDirectories(logPath);
        }
        
        startWriterExecutor();
        startFlushScheduler();
    }

    /**
     * 启动异步写入线程池
     */
    private void startWriterExecutor() {
        writerExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "log-file-writer");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 启动定时刷新调度器
     */
    private void startFlushScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "log-file-flush-scheduler");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(() -> {
            try {
                flushAllToFile();
            } catch (Exception e) {
                logger.error("定时刷新日志到文件时发生异常", e);
            }
        }, flushIntervalMinutes, flushIntervalMinutes, TimeUnit.MINUTES);
    }


    /**
     * 添加日志到缓冲区
     * <p>
     * 日志会被添加到对应应用的缓冲区，当待写入日志数达到阈值时，自动触发文件写入。
     *
     * @param appCode    应用编码
     * @param version    版本号
     * @param logLevel   日志级别
     * @param logContent 日志内容
     * @param logTime    日志时间
     */
    public void addLog(String appCode, String version, String logLevel, 
                       String logContent, Date logTime) {
        LogFileBuffer buffer = getOrCreateBuffer(appCode);
        
        AppLog log = new AppLog();
        log.setAppCode(appCode);
        log.setVersion(version);
        log.setLogLevel(logLevel);
        log.setLogContent(logContent);
        log.setLogTime(logTime != null ? logTime : new Date());
        
        buffer.logs.offerLast(log);
        int pending = buffer.pendingCount.incrementAndGet();

        // 检查是否达到提交阈值
        if (pending >= flushSize) {
            asyncFlushToFile(appCode);
        }
    }

    /**
     * 异步刷新到文件
     */
    private void asyncFlushToFile(String appCode) {
        if (writerExecutor == null || writerExecutor.isShutdown()) {
            return;
        }
        writerExecutor.submit(() -> {
            try {
                flushToFile(appCode);
            } catch (Exception e) {
                logger.error("异步写入日志文件失败: appCode={}", appCode, e);
            }
        });
    }

    /**
     * 刷新指定应用的缓冲区到文件
     */
    public void flushToFile(String appCode) {
        LogFileBuffer buffer = buffers.get(appCode);
        if (buffer == null) {
            return;
        }

        if (!buffer.writeLock.tryLock()) {
            return;
        }

        try {
            int pending = buffer.pendingCount.get();
            if (pending == 0) {
                return;
            }

            // 收集待写入的日志
            List<AppLog> logsToWrite = new ArrayList<>();
            for (int i = 0; i < pending; i++) {
                AppLog log = buffer.logs.pollFirst();
                if (log != null) {
                    logsToWrite.add(log);
                }
            }
            buffer.pendingCount.addAndGet(-logsToWrite.size());

            if (logsToWrite.isEmpty()) {
                return;
            }

            // 写入文件
            writeLogsToFile(appCode, logsToWrite, buffer);
            
            logger.debug("应用[{}]日志写入完成，已写入 {} 条", appCode, logsToWrite.size());

        } finally {
            buffer.writeLock.unlock();
        }
    }


    /**
     * 写入日志到文件
     */
    private void writeLogsToFile(String appCode, List<AppLog> logs, 
                                  LogFileBuffer buffer) {
        if (logs.isEmpty()) {
            return;
        }
        
        try {
            // 确保目录存在
            Path logDir = Paths.get(logsDir, appCode);
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
            }

            // 获取版本号（从第一条日志获取）
            String version = logs.get(0).getVersion();
            if (version == null || version.isEmpty()) {
                version = "unknown";
            }
            // 清理版本号中的特殊字符
            String safeVersion = version.replaceAll("[/\\\\:*?\"<>|]", "_");

            // 检查是否需要创建新文件（版本变化或文件过大或首次写入）
            if (buffer.currentFile == null || 
                !safeVersion.equals(buffer.currentVersion) || 
                buffer.currentFileSize >= getMaxFileSize()) {
                
                // 版本变化时，开始新的运行/打包
                if (!safeVersion.equals(buffer.currentVersion)) {
                    buffer.currentVersion = safeVersion;
                    // 查找该版本的下一个运行次数
                    int[] counts = findNextRunAndFileSeq(logDir, appCode, safeVersion);
                    buffer.runCount = counts[0];
                    buffer.fileSeq = counts[1];
                } else if (buffer.currentFileSize >= getMaxFileSize()) {
                    // 文件过大时递增文件序号
                    buffer.fileSeq++;
                }
                
                // 创建新文件: appCode_version_x-y.log
                String fileName = String.format("%s_%s_%d-%d.log", 
                        appCode, safeVersion, buffer.runCount, buffer.fileSeq);
                buffer.currentFile = logDir.resolve(fileName).toFile();
                buffer.currentFileSize = buffer.currentFile.exists() ? buffer.currentFile.length() : 0;
            }

            // 写入日志
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(buffer.currentFile, true), 
                                           StandardCharsets.UTF_8))) {
                for (AppLog log : logs) {
                    String line = formatLogLine(log);
                    writer.write(line);
                    writer.newLine();
                    buffer.currentFileSize += line.getBytes(StandardCharsets.UTF_8).length + 1;
                    
                    // 检查是否需要滚动
                    if (buffer.currentFileSize >= getMaxFileSize()) {
                        writer.flush();
                        buffer.fileSeq++;
                        String fileName = String.format("%s_%s_%d-%d.log", 
                                appCode, buffer.currentVersion, buffer.runCount, buffer.fileSeq);
                        buffer.currentFile = logDir.resolve(fileName).toFile();
                        buffer.currentFileSize = 0;
                        break;
                    }
                }
            }

        } catch (IOException e) {
            logger.error("写入日志文件失败: appCode={}", appCode, e);
        }
    }

    /**
     * 查找当前最大运行次数
     * <p>
     * 用于startNewSession时确定下一个运行次数
     * 
     * @return 最大运行次数，如果没有文件则返回0
     */
    private int findMaxRunCount(Path logDir, String appCode, String version) {
        int maxRunCount = 0;
        String prefix = appCode + "_" + version + "_";
        
        File[] files = logDir.toFile().listFiles((dir, name) -> 
            name.startsWith(prefix) && name.endsWith(".log"));
        
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                try {
                    int start = prefix.length();
                    int end = name.lastIndexOf(".log");
                    if (end > start) {
                        String xyPart = name.substring(start, end);
                        String[] parts = xyPart.split("-");
                        if (parts.length == 2) {
                            int runCount = Integer.parseInt(parts[0]);
                            maxRunCount = Math.max(maxRunCount, runCount);
                        }
                    }
                } catch (NumberFormatException e) {
                    // 忽略无法解析的文件名
                }
            }
        }
        return maxRunCount;
    }

    /**
     * 查找下一个运行次数和文件序号
     * <p>
     * 文件命名规则: appCode_version_x-y.log
     * 
     * @return int[]{runCount, fileSeq}
     */
    private int[] findNextRunAndFileSeq(Path logDir, String appCode, String version) {
        int maxRunCount = 0;
        int maxFileSeq = 0;
        
        // 匹配模式: appCode_version_x-y.log
        String prefix = appCode + "_" + version + "_";
        
        File[] files = logDir.toFile().listFiles((dir, name) -> 
            name.startsWith(prefix) && name.endsWith(".log"));
        
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                try {
                    // 解析 x-y 部分
                    int start = prefix.length();
                    int end = name.lastIndexOf(".log");
                    if (end > start) {
                        String xyPart = name.substring(start, end);
                        String[] parts = xyPart.split("-");
                        if (parts.length == 2) {
                            int runCount = Integer.parseInt(parts[0]);
                            int fileSeq = Integer.parseInt(parts[1]);
                            
                            if (runCount > maxRunCount) {
                                maxRunCount = runCount;
                                maxFileSeq = fileSeq;
                            } else if (runCount == maxRunCount && fileSeq > maxFileSeq) {
                                maxFileSeq = fileSeq;
                            }
                        }
                    }
                } catch (NumberFormatException e) {
                    // 忽略无法解析的文件名
                }
            }
        }
        
        // 检查最后一个文件是否已满，决定是继续写入还是创建新文件
        if (maxRunCount > 0) {
            String lastFileName = String.format("%s_%s_%d-%d.log", appCode, version, maxRunCount, maxFileSeq);
            File lastFile = logDir.resolve(lastFileName).toFile();
            if (lastFile.exists() && lastFile.length() >= getMaxFileSize()) {
                // 文件已满，递增文件序号
                return new int[]{maxRunCount, maxFileSeq + 1};
            }
            // 文件未满，继续使用
            return new int[]{maxRunCount, maxFileSeq};
        }
        
        // 没有找到任何文件，从第1次开始
        return new int[]{1, 1};
    }

    /**
     * 格式化日志行
     */
    private String formatLogLine(AppLog log) {
        StringBuilder sb = new StringBuilder();
        sb.append(logTimeFormat.get().format(log.getLogTime()));
        sb.append(" ");
        sb.append(log.getLogContent());
        return sb.toString();
    }


    /**
     * 刷新所有缓冲区到文件
     */
    public void flushAllToFile() {
        for (String appCode : buffers.keySet()) {
            flushToFile(appCode);
        }
    }

    /**
     * 开始新的运行/打包会话
     * <p>
     * 调用此方法后，下次写入日志时会递增运行次数（x），文件序号（y）重置为1
     *
     * @param appCode 应用编码
     * @param version 版本号
     */
    public void startNewSession(String appCode, String version) {
        LogFileBuffer buffer = getOrCreateBuffer(appCode);
        buffer.writeLock.lock();
        try {
            // 先刷新现有日志
            flushBufferInternal(appCode, buffer);
            
            // 清理版本号
            String safeVersion = version != null ? version.replaceAll("[/\\\\:*?\"<>|]", "_") : "unknown";
            
            // 确保目录存在
            Path logDir = Paths.get(logsDir, appCode);
            try {
                if (!Files.exists(logDir)) {
                    Files.createDirectories(logDir);
                }
            } catch (IOException e) {
                logger.error("创建日志目录失败: {}", logDir, e);
            }
            
            // 查找当前最大运行次数并递增
            int maxRunCount = findMaxRunCount(logDir, appCode, safeVersion);
            buffer.runCount = maxRunCount + 1;
            buffer.fileSeq = 1;
            buffer.currentVersion = safeVersion;
            buffer.currentFile = null;
            buffer.currentFileSize = 0;
            
            logger.info("开始新的会话: appCode={}, version={}, runCount={}", appCode, version, buffer.runCount);
        } finally {
            buffer.writeLock.unlock();
        }
    }

    /**
     * 内部刷新方法（需要在持有锁的情况下调用）
     */
    private void flushBufferInternal(String appCode, LogFileBuffer buffer) {
        int pending = buffer.pendingCount.get();
        if (pending == 0) {
            return;
        }

        List<AppLog> logsToWrite = new ArrayList<>();
        for (int i = 0; i < pending; i++) {
            AppLog log = buffer.logs.pollFirst();
            if (log != null) {
                logsToWrite.add(log);
            }
        }
        buffer.pendingCount.addAndGet(-logsToWrite.size());

        if (!logsToWrite.isEmpty()) {
            writeLogsToFile(appCode, logsToWrite, buffer);
        }
    }

    @PreDestroy
    public void shutdown() {
        logger.info("正在关闭日志文件写入服务...");
        
        // 刷新所有缓冲区
        flushAllToFile();
        
        // 关闭调度器
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.warn("日志文件刷新调度器未能在10秒内关闭，强制关闭");
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.error("日志文件刷新调度器关闭时被中断", e);
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // 关闭写入线程池
        if (writerExecutor != null && !writerExecutor.isShutdown()) {
            writerExecutor.shutdown();
            try {
                if (!writerExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.warn("日志文件写入线程池未能在10秒内关闭，强制关闭");
                    writerExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.error("日志文件写入线程池关闭时被中断", e);
                writerExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        logger.info("日志文件写入服务已关闭");
    }

}
