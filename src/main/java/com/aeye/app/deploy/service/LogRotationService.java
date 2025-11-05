package com.aeye.app.deploy.service;

import com.aeye.app.deploy.model.AppInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LogRotationService implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(LogRotationService.class);

    @Value("${app.log.rotation.enabled:true}")
    private boolean rotationEnabled;

    @Value("${app.log.rotation.max-size-mb:10}")
    double maxSizeMb;

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

    private void checkAndRotateLogs() {
        try {
            List<AppInfo> apps = appMgtService.getAllApps();
            if (apps == null || apps.isEmpty()) {
                return;
            }

            double maxSizeBytes = maxSizeMb * 1024L * 1024L;

            for (AppInfo app : apps) {
                String logFilePath = app.getLogFile();
                if (logFilePath == null || logFilePath.isEmpty()) {
                    continue;
                }

                File logFile = new File(logFilePath);
                if (!logFile.exists()) {
                    continue;
                }

                long fileSize = logFile.length();
                if (fileSize >= maxSizeBytes) {
                    logger.info("日志文件超过限制: {} ({} MB), 开始滚动", logFilePath, fileSize / (1024.0 * 1024.0));
                    rotateLogFile(logFile);
                }
            }
        } catch (Exception e) {
            logger.error("检查日志文件时发生异常", e);
        }
    }

    private void rotateLogFile(File currentLogFile) {
        try {
            String currentPath = currentLogFile.getAbsolutePath();
            String parentDir = currentLogFile.getParent();
            String fileName = currentLogFile.getName();

            Pattern pattern = Pattern.compile("(.*_)(\\d{8})_(\\d{6})\\.log");
            Matcher matcher = pattern.matcher(fileName);
            String archivedFileName;
            if (matcher.find()) {
                String baseName = matcher.group(1);
                archivedFileName = String.format("%s%s.log", baseName, new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()));
            } else {
                String baseName = fileName;
                if (fileName.endsWith(".log")) {
                    baseName = fileName.substring(0, fileName.length() - 4);
                }
                archivedFileName = generateArchivedFileName(parentDir, baseName);
            }

            File archivedFile = new File(parentDir, archivedFileName);

            logger.info("归档日志文件: {} -> {}", currentPath, archivedFile.getAbsolutePath());
            
            Files.copy(currentLogFile.toPath(), archivedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            
            try (RandomAccessFile raf = new RandomAccessFile(currentLogFile, "rw")) {
                raf.setLength(0);
                logger.info("已清空日志文件: {}", currentPath);
            }

        } catch (IOException e) {
            logger.error("滚动日志文件时发生异常: {}", currentLogFile.getAbsolutePath(), e);
        }
    }

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
     * 应用关闭时清理资源
     */
    @PreDestroy
    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            logger.info("正在关闭日志轮转调度器...");
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warn("日志轮转调度器未能在5秒内关闭，强制关闭");
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                logger.error("日志轮转调度器关闭时被中断", e);
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            logger.info("日志轮转调度器已关闭");
        }
    }

}

