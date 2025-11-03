package com.aeye.app.deploy.service;

import com.aeye.app.deploy.model.AppInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 日志文件滚动服务
 * 监控日志文件大小，超过指定大小时自动分割文件
 */
@Service
public class LogRotationService implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(LogRotationService.class);

    @Value("${app.log.rotation.enabled:true}")
    private boolean rotationEnabled;

    @Value("${app.log.rotation.max-size-mb:10}")
    private int maxSizeMb;

    @Value("${app.log.rotation.check-interval-seconds:60}")
    private int checkIntervalSeconds;

    @Autowired
    private AppMgtService appMgtService;

    private ScheduledExecutorService scheduler;

    @Override
    public void run(String... args) throws Exception {
        if (rotationEnabled) {
            logger.info("日志滚动服务启动，最大文件大小: {} MB，检查间隔: {} 秒", maxSizeMb, checkIntervalSeconds);
            startRotationScheduler();
        } else {
            logger.info("日志滚动服务已禁用");
        }
    }

    /**
     * 启动日志滚动调度器
     */
    private void startRotationScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "LogRotationScheduler");
            thread.setDaemon(true);
            return thread;
        });

        scheduler.scheduleWithFixedDelay(() -> {
            try {
                checkAndRotateLogs();
            } catch (Exception e) {
                logger.error("日志滚动检查时发生异常", e);
            }
        }, 60, checkIntervalSeconds, TimeUnit.SECONDS);

        logger.info("日志滚动调度器已启动");
    }

    /**
     * 检查并滚动日志文件
     */
    private void checkAndRotateLogs() {
        try {
            List<AppInfo> apps = appMgtService.getAllApps();
            if (apps == null || apps.isEmpty()) {
                return;
            }

            long maxSizeBytes = maxSizeMb * 1024L * 1024L;

            for (AppInfo app : apps) {
                String logFilePath = app.getLogFile();
                if (logFilePath == null || logFilePath.isEmpty()) {
                    continue;
                }

                File logFile = new File(logFilePath);
                if (!logFile.exists()) {
                    continue;
                }

                // 检查文件大小
                long fileSize = logFile.length();
                if (fileSize >= maxSizeBytes) {
                    logger.info("日志文件超过限制: {} ({} MB), 开始滚动", 
                               logFilePath, fileSize / (1024.0 * 1024.0));
                    rotateLogFile(app, logFile);
                }
            }
        } catch (Exception e) {
            logger.error("检查日志文件时发生异常", e);
        }
    }

    /**
     * 滚动日志文件
     */
    private void rotateLogFile(AppInfo app, File currentLogFile) {
        try {
            String currentPath = currentLogFile.getAbsolutePath();
            String parentDir = currentLogFile.getParent();
            String fileName = currentLogFile.getName();

            // 移除 .log 扩展名
            String baseName = fileName;
            if (fileName.endsWith(".log")) {
                baseName = fileName.substring(0, fileName.length() - 4);
            }

            // 生成归档文件名：原文件名.序号.log
            String archivedFileName = generateArchivedFileName(parentDir, baseName);
            File archivedFile = new File(parentDir, archivedFileName);

            // 重命名当前日志文件为归档文件
            logger.info("归档日志文件: {} -> {}", currentPath, archivedFile.getAbsolutePath());
            Files.move(currentLogFile.toPath(), archivedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // 创建新的空日志文件
            boolean created = currentLogFile.createNewFile();
            if (created) {
                logger.info("创建新日志文件: {}", currentPath);
                
                // 更新AppInfo的logFile字段（保持不变，继续使用同一个文件名）
                // 不需要更新，因为新文件使用相同的名称
                logger.info("应用 {} 继续使用日志文件: {}", app.getAppCode(), currentPath);
            } else {
                logger.warn("创建新日志文件失败: {}", currentPath);
            }

        } catch (IOException e) {
            logger.error("滚动日志文件时发生异常: {}", currentLogFile.getAbsolutePath(), e);
        }
    }

    /**
     * 生成归档文件名
     * 格式: baseName.1.log, baseName.2.log, ...
     */
    private String generateArchivedFileName(String parentDir, String baseName) {
        int index = 1;
        while (true) {
            String candidateName = String.format("%s.%d.log", baseName, index);
            File candidateFile = new File(parentDir, candidateName);
            if (!candidateFile.exists()) {
                return candidateName;
            }
            index++;
        }
    }

    /**
     * 停止调度器
     */
    public void stopScheduler() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
                logger.info("日志滚动调度器已停止");
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}

