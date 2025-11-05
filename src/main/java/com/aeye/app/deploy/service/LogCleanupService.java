package com.aeye.app.deploy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.File;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class LogCleanupService implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(LogCleanupService.class);

    @Value("${app.directory.logs:/home/logs}")
    private String logsDir;

    @Value("${app.log.cleanup.enabled:true}")
    private boolean cleanupEnabled;

    @Value("${app.log.cleanup.retention-days:7}")
    private int retentionDays;

    private ScheduledExecutorService scheduler;

    @Override
    public void run(String... args) throws Exception {
        if (cleanupEnabled) {
            logger.info("日志清理服务启动，保留天数: {} 天", retentionDays);
            
            // 系统启动时立即执行一次清理
            logger.info("系统启动时执行初始日志清理...");
            cleanupOldLogs();
            
            // 启动定时清理调度器
            startCleanupScheduler();
        } else {
            logger.info("日志清理服务已禁用");
        }
    }

    /**
     * 启动日志清理调度器
     */
    private void startCleanupScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "log-cleanup-thread");
            thread.setDaemon(true);
            return thread;
        });

        // 每天凌晨2点执行清理任务
        scheduler.scheduleAtFixedRate(this::cleanupOldLogs, 
            getInitialDelay(), 
            24 * 60 * 60 * 1000L, // 24小时
            TimeUnit.MILLISECONDS);

        logger.info("日志清理调度器已启动，每天凌晨2点执行清理任务");
    }

    /**
     * 计算初始延迟时间（到下一个凌晨2点的毫秒数）
     */
    private long getInitialDelay() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.withHour(2).withMinute(0).withSecond(0).withNano(0);
        
        // 如果当前时间已经过了凌晨2点，则设置为明天的凌晨2点
        if (now.isAfter(nextRun)) {
            nextRun = nextRun.plusDays(1);
        }

        return java.time.Duration.between(now, nextRun).toMillis();
    }

    /**
     * 清理旧的日志文件
     */
    public void cleanupOldLogs() {
        try {
            logger.info("开始清理 {} 天前的日志文件...", retentionDays);
            
            File logsDirectory = new File(logsDir);
            if (!logsDirectory.exists() || !logsDirectory.isDirectory()) {
                logger.warn("日志目录不存在或不是目录: {}", logsDir);
                return;
            }

            long cutoffTime = System.currentTimeMillis() - (retentionDays * 24L * 60L * 60L * 1000L);
            int deletedCount = 0;
            long totalSize = 0;
            int totalFiles = 0;

            // 遍历日志目录下的所有文件
            File[] files = logsDirectory.listFiles((dir, name) -> name.toLowerCase().endsWith(".log"));
            if (files == null) {
                logger.warn("无法读取日志目录: {}", logsDir);
                return;
            }

            totalFiles = files.length;
            logger.info("日志目录中共有 {} 个日志文件", totalFiles);

            for (File file : files) {
                if (file.isFile() && file.lastModified() < cutoffTime) {
                    long fileSize = file.length();
                    if (file.delete()) {
                        deletedCount++;
                        totalSize += fileSize;
                        logger.debug("删除过期日志文件: {} (大小: {} bytes)", file.getName(), fileSize);
                    } else {
                        logger.warn("删除日志文件失败: {}", file.getName());
                    }
                }
            }

            if (deletedCount > 0) {
                logger.info("日志清理完成，删除了 {} 个过期文件，释放空间: {} MB", 
                    deletedCount, totalSize / (1024 * 1024));
            } else {
                logger.info("日志清理完成，没有找到需要删除的过期文件");
            }

        } catch (Exception e) {
            logger.error("清理日志文件时发生错误", e);
        }
    }

    /**
     * 应用关闭时清理资源
     */
    @PreDestroy
    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            logger.info("正在关闭日志清理调度器...");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warn("日志清理调度器未能在5秒内关闭，强制关闭");
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.error("日志清理调度器关闭时被中断", e);
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            logger.info("日志清理调度器已关闭");
        }
    }
}
