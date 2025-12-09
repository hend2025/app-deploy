package com.aeye.app.deploy.service;

import com.aeye.app.deploy.model.VerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class BuildTaskService {
    
    private static final Logger logger = LoggerFactory.getLogger(BuildTaskService.class);

    @Autowired
    private VerMgtService verMgtService;
    
    @Autowired
    private LogBufferService logBufferService;

    private final Map<String, Process> cmdMap = new ConcurrentHashMap<>();
    
    private static final int MAX_CONCURRENT_BUILDS = 5;
    private final ExecutorService executorService = Executors.newFixedThreadPool(MAX_CONCURRENT_BUILDS, r -> {
        Thread thread = new Thread(r, "build-task-thread");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 判断当前操作系统是否为Windows
     */
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /**
     * 获取脚本内容（根据操作系统选择）
     */
    private String getScriptContent(VerInfo appVersion) {
        if (isWindows()) {
            return appVersion.getScriptCmd();
        } else {
            return appVersion.getScriptSh();
        }
    }

    /**
     * 创建临时脚本文件
     */
    private File createTempScript(String appCode, String scriptContent, String targetVersion) throws IOException {
        // 替换脚本中的版本号占位符
        String processedContent = scriptContent.replace("${VERSION}", targetVersion)
                                               .replace("$VERSION", targetVersion);

        String extension = isWindows() ? ".cmd" : ".sh";
        File scriptFile = File.createTempFile("build_" + appCode + "_", extension);
        
        if (isWindows()) {
            // Windows: 在脚本开头添加chcp 65001设置UTF-8编码
            // 先统一为LF，再转换为CRLF
            processedContent = processedContent.replace("\r\n", "\n").replace("\r", "\n");
            processedContent = processedContent.replace("\n", "\r\n");
            // 添加UTF-8编码设置
            processedContent = "@echo off\r\nchcp 65001 >nul\r\n" + processedContent;
            
            // 使用UTF-8编码写入
            try (java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(
                    new java.io.FileOutputStream(scriptFile), StandardCharsets.UTF_8)) {
                writer.write(processedContent);
            }
        } else {
            // Linux: 使用UTF-8编码，LF换行符
            try (java.io.BufferedWriter writer = Files.newBufferedWriter(scriptFile.toPath(), StandardCharsets.UTF_8)) {
                writer.write(processedContent);
            }
            scriptFile.setExecutable(true);
        }

        return scriptFile;
    }

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
                cmdMap.remove(appCode);
            }
        }
        
        // 检查并发构建任务数
        int activeBuilds = (int) cmdMap.values().stream()
                .filter(p -> p != null && p.isAlive())
                .count();
        if (activeBuilds >= MAX_CONCURRENT_BUILDS) {
            logger.warn("当前并发构建任务数已达到上限: {}", activeBuilds);
            throw new IllegalStateException("当前并发构建任务数已达到上限(" + MAX_CONCURRENT_BUILDS + ")，请等待其他构建任务完成");
        }

        // 获取脚本内容
        String scriptContent = getScriptContent(appVersion);
        if (scriptContent == null || scriptContent.trim().isEmpty()) {
            String osType = isWindows() ? "Windows(script_cmd)" : "Linux(script_sh)";
            throw new IllegalStateException("未配置" + osType + "构建脚本");
        }
        
        // 立即更新状态为构建中
        verMgtService.updateStatus(appCode, "1", null);
        
        // 记录构建开始日志
        logBufferService.addLog(appCode, targetVersion, "INFO", 
            String.format("开始构建任务: %s, 目标版本: %s", appCode, targetVersion), new Date());
        
        executorService.submit(() -> {
            Process process = null;
            File tempScriptFile = null;
            try {
                logger.info("开始构建任务: {}, 版本: {}", appCode, targetVersion);

                // 创建临时脚本文件
                tempScriptFile = createTempScript(appCode, scriptContent, targetVersion);
                logger.info("创建临时脚本: {}", tempScriptFile.getAbsolutePath());

                // 执行脚本
                ProcessBuilder processBuilder = new ProcessBuilder();
                if (isWindows()) {
                    processBuilder.command("cmd", "/c", tempScriptFile.getAbsolutePath(), targetVersion);
                } else {
                    processBuilder.command("bash", tempScriptFile.getAbsolutePath(), targetVersion);
                }

                // 合并标准输出和错误输出
                processBuilder.redirectErrorStream(true);
                
                process = processBuilder.start();
                cmdMap.put(appCode, process);
                
                // 读取进程输出并写入内存缓冲
                // Maven/Java输出通常是UTF-8编码
                final Process finalProcess = process;
                Thread outputReader = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(finalProcess.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            logBufferService.addLog(appCode, targetVersion, parseLogLevel(line), line, new Date());
                        }
                    } catch (Exception e) {
                        logger.error("读取构建输出失败: {}", appCode, e);
                    }
                }, "build-log-reader-" + appCode);
                outputReader.setDaemon(true);
                outputReader.start();

                int exitCode = process.waitFor();

                // 更新任务状态
                if (exitCode == 0) {
                    logger.info("构建成功: {}, 版本: {}", appCode, targetVersion);
                    logBufferService.addLog(appCode, targetVersion, "INFO", 
                        String.format("构建成功，退出码: %d", exitCode), new Date());
                    verMgtService.updateStatus(appCode, "0", targetVersion);
                } else {
                    logger.warn("构建失败: {}, 版本: {}, 退出码: {}", appCode, targetVersion, exitCode);
                    logBufferService.addLog(appCode, targetVersion, "ERROR", 
                        String.format("构建失败，退出码: %d", exitCode), new Date());
                    verMgtService.updateStatus(appCode, "0", null);
                }

            } catch (Exception e) {
                logger.error("构建任务异常: {}, 版本: {}", appCode, targetVersion, e);
                logBufferService.addLog(appCode, targetVersion, "ERROR", 
                    "构建任务异常: " + e.getMessage(), new Date());
                try {
                    verMgtService.updateStatus(appCode, "0", null);
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
                // 清理临时脚本文件
                if (tempScriptFile != null && tempScriptFile.exists()) {
                    try {
                        Files.delete(tempScriptFile.toPath());
                        logger.debug("已删除临时脚本: {}", tempScriptFile.getAbsolutePath());
                    } catch (Exception e) {
                        logger.warn("删除临时脚本失败: {}", tempScriptFile.getAbsolutePath());
                    }
                }
            }
        });
    }
    
    /**
     * 解析日志级别
     */
    private String parseLogLevel(String logContent) {
        if (logContent == null) {
            return "INFO";
        }
        String upper = logContent.toUpperCase();
        if (upper.contains("ERROR") || upper.contains("FATAL")) {
            return "ERROR";
        } else if (upper.contains("WARN")) {
            return "WARN";
        } else if (upper.contains("DEBUG")) {
            return "DEBUG";
        }
        return "INFO";
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
                process.destroy();
                boolean terminated = process.waitFor(3, TimeUnit.SECONDS);
                if (!terminated) {
                    logger.warn("构建任务未在3秒内结束，强制终止: {}", appCode);
                    process.destroyForcibly();
                }
                verMgtService.updateStatus(appCode, "0", null);
                return true;
            } catch (Exception e) {
                logger.error("停止构建任务失败: {}", appCode, e);
                cmdMap.remove(appCode);
                return false;
            }
        }
        if (process != null) {
            cmdMap.remove(appCode);
        }
        logger.warn("未找到运行中的构建任务: {}", appCode);
        return false;
    }

    @PreDestroy
    public void shutdown() {
        logger.info("正在关闭BuildTaskService线程池...");

        for (Map.Entry<String, Process> entry : cmdMap.entrySet()) {
            try {
                logger.info("停止构建任务: {}", entry.getKey());
                entry.getValue().destroy();
            } catch (Exception e) {
                logger.error("停止构建任务失败: {}", entry.getKey(), e);
            }
        }
        cmdMap.clear();

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
