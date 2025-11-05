package com.aeye.app.deploy.service;

import com.aeye.app.deploy.model.VerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class BuildTaskService {
    
    private static final Logger logger = LoggerFactory.getLogger(BuildTaskService.class);
    
    @Value("${app.directory.logs:/home/logs}")
    private String logsDir;
    @Autowired
    private VerMgtService VerMgtService;

    private final Map<String, Process> cmdMap = new ConcurrentHashMap<>();
    
    // 使用固定大小线程池管理构建任务，限制并发数，避免资源耗尽
    private static final int MAX_CONCURRENT_BUILDS = 5;
    private final ExecutorService executorService = Executors.newFixedThreadPool(MAX_CONCURRENT_BUILDS, r -> {
        Thread thread = new Thread(r, "build-task-thread");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 启动构建任务
     */
    public void startBuild(VerInfo appVersion, String targetVersion) {
        String appCode = appVersion.getAppCode();
        
        // 检查是否已有构建任务在运行
        if (cmdMap.containsKey(appCode)) {
            Process existingProcess = cmdMap.get(appCode);
            if (existingProcess != null && existingProcess.isAlive()) {
                logger.warn("构建任务已在运行中: {}", appCode);
                throw new IllegalStateException("该应用的构建任务正在运行中，请先停止后再启动");
            } else {
                // 进程已结束但未清理，清理缓存
                cmdMap.remove(appCode);
            }
        }
        
        // 检查并发构建任务数，避免资源耗尽
        int activeBuilds = (int) cmdMap.values().stream()
                .filter(p -> p != null && p.isAlive())
                .count();
        if (activeBuilds >= MAX_CONCURRENT_BUILDS) {
            logger.warn("当前并发构建任务数已达到上限: {}", activeBuilds);
            throw new IllegalStateException("当前并发构建任务数已达到上限(" + MAX_CONCURRENT_BUILDS + ")，请等待其他构建任务完成");
        }
        
        executorService.submit(() -> {
            Process process = null;
            try {
                logger.info("开始构建任务: {}, 版本: {}", appCode, targetVersion);
                
                // 创建日志文件路径：应用名称_版本号_build_时间.log
                String logFileName = String.format("build_%s_%s_%s.log",
                    appVersion.getAppCode(),
                    targetVersion,
                    new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()));
                String logFilePath = logsDir + "/" + logFileName;
                appVersion.setLogFile(logFilePath);
                
                // 确保日志目录存在
                File logDir = new File(logsDir);
                if (!logDir.exists()) {
                    logDir.mkdirs();
                }

                // 执行构建脚本
                String scriptPath = appVersion.getScript();
                ProcessBuilder processBuilder = new ProcessBuilder();
                if (scriptPath.endsWith(".cmd") || scriptPath.endsWith(".bat")) {
                    processBuilder.command("cmd", "/c", "chcp 65001 >nul && " + scriptPath, targetVersion);
                } else if (scriptPath.endsWith(".sh")) {
                    processBuilder.command("bash", scriptPath, targetVersion);
                } else {
                    processBuilder.command(scriptPath, targetVersion);
                }

                processBuilder.directory(new File(scriptPath).getParentFile());
                processBuilder.redirectOutput(new File(logFilePath));
                processBuilder.redirectError(new File(logFilePath));
                
                process = processBuilder.start();

                VerMgtService.updateStatus(appCode, "1", null);
                cmdMap.put(appCode, process);

                int exitCode = process.waitFor();

                // 更新任务状态
                if (exitCode == 0) {
                    logger.info("构建成功: {}, 版本: {}", appCode, targetVersion);
                    VerMgtService.updateStatus(appCode, "0", targetVersion);
                } else {
                    logger.warn("构建失败: {}, 版本: {}, 退出码: {}", appCode, targetVersion, exitCode);
                    VerMgtService.updateStatus(appCode, "0", null);
                }

            } catch (Exception e) {
                logger.error("构建任务异常: {}, 版本: {}", appCode, targetVersion, e);
                // 构建异常时也更新状态为失败
                try {
                    VerMgtService.updateStatus(appCode, "0", null);
                } catch (Exception ex) {
                    logger.error("更新构建状态失败: {}", appCode, ex);
                }
            } finally {
                cmdMap.remove(appCode);
                if (process != null && process.isAlive()) {
                    try {
                        process.destroy();
                    } catch (Exception e) {
                        logger.error("销毁进程失败", e);
                    }
                }
            }
        });

    }

    /**
     * 停止构建任务
     */
    public boolean stopBuild(String appCode) {
        Process process = cmdMap.get(appCode);
        if (process != null && process.isAlive()) {
            logger.info("停止构建任务: {}", appCode);
            cmdMap.remove(appCode);
            try {
                // 先尝试优雅关闭
                process.destroy();
                // 等待3秒，如果进程未结束则强制终止
                boolean terminated = process.waitFor(3, TimeUnit.SECONDS);
                if (!terminated) {
                    logger.warn("构建任务未在3秒内结束，强制终止: {}", appCode);
                    process.destroyForcibly();
                }
                // 更新状态为已停止
                VerMgtService.updateStatus(appCode, "0", null);
                return true;
            } catch (Exception e) {
                logger.error("停止构建任务失败: {}", appCode, e);
                // 即使停止失败也清理缓存
                cmdMap.remove(appCode);
                return false;
            }
        }
        // 进程不存在或已结束，清理缓存
        if (process != null) {
            cmdMap.remove(appCode);
        }
        logger.warn("未找到运行中的构建任务: {}", appCode);
        return false;
    }

    /**
     * 应用关闭时优雅关闭线程池
     */
    @PreDestroy
    public void shutdown() {
        logger.info("正在关闭BuildTaskService线程池...");

        // 停止所有正在运行的构建任务
        for (Map.Entry<String, Process> entry : cmdMap.entrySet()) {
            try {
                logger.info("停止构建任务: {}", entry.getKey());
                entry.getValue().destroy();
            } catch (Exception e) {
                logger.error("停止构建任务失败: {}", entry.getKey(), e);
            }
        }
        cmdMap.clear();

        // 关闭线程池
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warn("线程池未能在10秒内关闭，强制关闭");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            logger.error("线程池关闭时被中断", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("BuildTaskService线程池已关闭");
    }

}
