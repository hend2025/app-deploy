package com.aeye.app.deploy.service;

import com.aeye.app.deploy.model.AppBuild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class BuildTaskService {
    
    private static final Logger logger = LoggerFactory.getLogger(BuildTaskService.class);

    @Autowired
    private AppBuildService appBuildService;
    
    @Autowired
    private LogBufferService logBufferService;

    @Autowired
    private LogFileWriterService logFileWriterService;

    @Autowired
    private GitService gitService;

    @Value("${app.directory.workspace}")
    private String workspaceDir;

    @Value("${app.directory.archive}")
    private String archiveDir;

    private final Map<String, Process> cmdMap = new ConcurrentHashMap<>();
    
    @Value("${app.process.max-concurrent-builds:10}")
    private int maxConcurrentBuilds;
    
    private ExecutorService executorService;
    
    @javax.annotation.PostConstruct
    public void init() {
        executorService = Executors.newFixedThreadPool(maxConcurrentBuilds, r -> {
            Thread thread = new Thread(r, "build-task-thread");
            thread.setDaemon(true);
            return thread;
        });
        logger.info("BuildTaskService初始化完成，最大并发构建数: {}", maxConcurrentBuilds);
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private File createTempScript(String appCode, String scriptContent, String targetVersion) throws IOException {
        String processedContent = scriptContent;

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
            try (java.io.OutputStreamWriter writer = new java.io.OutputStreamWriter(new java.io.FileOutputStream(scriptFile), StandardCharsets.UTF_8)) {
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

    public void startBuild(AppBuild appVersion, String branchOrTag) {
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
        if (activeBuilds >= maxConcurrentBuilds) {
            logger.warn("当前并发构建任务数已达到上限: {}", activeBuilds);
            throw new IllegalStateException("当前并发构建任务数已达到上限(" + maxConcurrentBuilds + ")，请等待其他构建任务完成");
        }

        // 获取构建脚本
        final String scriptContent = appVersion.getBuildScript();
        if (scriptContent == null || scriptContent.trim().isEmpty()) {
            throw new IllegalStateException("未配置构建脚本");
        }
        
        // 立即更新状态为构建中
        appBuildService.updateStatus(appCode, "1", null);
        
        // 开始新的构建会话（清除缓存并递增打包次数）
        logBufferService.startNewSession(appCode, branchOrTag);
        
        // 记录构建开始日志
        logBufferService.addLog(appCode, branchOrTag, "INFO", 
            String.format("开始构建任务: %s, 分支/Tag: %s", appCode, branchOrTag), new Date());
        
        executorService.submit(() -> {
            Process process = null;
            File tempScriptFile = null;
            String workDir = null;
            try {
                logger.info("开始构建任务: {}, 分支/Tag: {}", appCode, branchOrTag);

                // 步骤1：拉取代码（如果配置了Git信息）
                if (appVersion.getGitUrl() != null && !appVersion.getGitUrl().trim().isEmpty()) {
                    logBufferService.addLog(appCode, branchOrTag, "INFO", "===== 步骤1: 拉取代码 =====", new Date());
                    workDir = gitService.cloneOrPull(
                        appCode, 
                        appVersion.getGitUrl(), 
                        appVersion.getGitAcct(), 
                        appVersion.getGitPwd(), 
                        branchOrTag,
                        (level, msg) -> logBufferService.addLog(appCode, branchOrTag, level, msg, new Date())
                    );
                } else {
                    workDir = new File(workspaceDir, appCode).getAbsolutePath();
                    logBufferService.addLog(appCode, branchOrTag, "WARN", "未配置Git信息，跳过代码拉取", new Date());
                }

                // 步骤2：执行构建脚本
                logBufferService.addLog(appCode, branchOrTag, "INFO", "===== 步骤2: 执行构建脚本 =====", new Date());
                
                // 创建临时脚本文件
                tempScriptFile = createTempScript(appCode, scriptContent, branchOrTag);
                logger.info("创建临时脚本: {}", tempScriptFile.getAbsolutePath());

                // 执行脚本
                ProcessBuilder processBuilder = new ProcessBuilder();
                if (isWindows()) {
                    processBuilder.command("cmd", "/c", tempScriptFile.getAbsolutePath(), branchOrTag);
                } else {
                    // 使用登录shell (-l) 以加载 .bash_profile/.bashrc 中的环境变量（如nvm）
                    processBuilder.command("bash", "-l", tempScriptFile.getAbsolutePath(), branchOrTag);
                }
                
                // 设置工作目录
                processBuilder.directory(new File(workDir));
                processBuilder.redirectErrorStream(true);

                process = processBuilder.start();
                cmdMap.put(appCode, process);
                
                // 读取进程输出并写入内存缓冲
                final Process finalProcess = process;
                final java.util.concurrent.CountDownLatch outputLatch = new java.util.concurrent.CountDownLatch(1);
                Thread outputReader = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(finalProcess.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            logBufferService.addLog(appCode, branchOrTag, parseLogLevel(line), line, new Date());
                        }
                    } catch (Exception e) {
                        logger.error("读取构建输出失败: {}", appCode, e);
                    } finally {
                        outputLatch.countDown();
                    }
                }, "build-log-reader-" + appCode);
                outputReader.setDaemon(true);
                outputReader.start();

                int exitCode = process.waitFor();
                
                // 等待日志读取线程完成，最多等待5秒
                try {
                    outputLatch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                // 更新任务状态
                if (exitCode == 0) {
                    logger.info("构建成功: {}, 分支/Tag: {}", appCode, branchOrTag);
                    logBufferService.addLog(appCode, branchOrTag, "INFO", 
                        String.format("构建成功，退出码: %d", exitCode), new Date());
                    
                    // 步骤3：归档文件
                    if (appVersion.getArchiveFiles() != null && !appVersion.getArchiveFiles().trim().isEmpty()) {
                        logBufferService.addLog(appCode, branchOrTag, "INFO", "===== 步骤3: 归档文件 =====", new Date());
                        archiveFiles(appCode, branchOrTag, workDir, appVersion.getArchiveFiles(),
                            (level, msg) -> logBufferService.addLog(appCode, branchOrTag, level, msg, new Date()));
                    }
                    
                    appBuildService.updateStatus(appCode, "0", branchOrTag);
                } else {
                    logger.warn("构建失败: {}, 分支/Tag: {}, 退出码: {}", appCode, branchOrTag, exitCode);
                    logBufferService.addLog(appCode, branchOrTag, "ERROR", 
                        String.format("构建失败，退出码: %d", exitCode), new Date());
                    appBuildService.updateStatus(appCode, "0", null);
                }

            } catch (Exception e) {
                logger.error("构建任务异常: {}, 分支/Tag: {}", appCode, branchOrTag, e);
                logBufferService.addLog(appCode, branchOrTag, "ERROR", 
                    "构建任务异常: " + e.getMessage(), new Date());
                try {
                    appBuildService.updateStatus(appCode, "0", null);
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
                // 构建完成后立即刷新日志到文件
                try {
                    logFileWriterService.flushToFile(appCode);
                    logger.info("构建日志已保存到文件: {}", appCode);
                } catch (Exception e) {
                    logger.error("刷新构建日志到文件失败: {}", appCode, e);
                }
            }
        });
    }


    private void archiveFiles(String appCode, String branchOrTag, String workDir, String archiveFiles,
                              java.util.function.BiConsumer<String, String> logConsumer) {
        // 创建归档目录：archiveDir/appCode/
        File appArchivePath = new File(archiveDir, appCode);
        if (!appArchivePath.exists()) {
            appArchivePath.mkdirs();
        }
        
        // 清理分支/tag名称中的特殊字符
        String safeBranchName = branchOrTag.replaceAll("[/\\\\:*?\"<>|]", "_");
        
        String[] patterns = archiveFiles.split(",");
        for (String pattern : patterns) {
            pattern = pattern.trim();
            if (pattern.isEmpty()) continue;
            
            // 检查是否为glob模式
            if (pattern.contains("*") || pattern.contains("?")) {
                // 使用glob模式匹配文件
                try {
                    Path workPath = java.nio.file.Paths.get(workDir);
                    String globPattern = "glob:" + pattern;
                    java.nio.file.PathMatcher matcher = java.nio.file.FileSystems.getDefault().getPathMatcher(globPattern);
                    
                    List<Path> matchedFiles = new ArrayList<>();
                    Files.walkFileTree(workPath, new java.nio.file.SimpleFileVisitor<Path>() {
                        @Override
                        public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
                            Path relativePath = workPath.relativize(file);
                            if (matcher.matches(relativePath)) {
                                matchedFiles.add(file);
                            }
                            return java.nio.file.FileVisitResult.CONTINUE;
                        }
                    });
                    
                    if (matchedFiles.isEmpty()) {
                        logConsumer.accept("WARN", "未找到匹配的文件: " + pattern);
                        continue;
                    }
                    
                    for (Path matchedFile : matchedFiles) {
                        archiveSingleFile(matchedFile.toFile(), appArchivePath, safeBranchName, logConsumer);
                    }
                } catch (IOException e) {
                    logConsumer.accept("ERROR", "遍历目录失败: " + pattern + ", 原因: " + e.getMessage());
                }
            } else {
                // 普通文件路径
                File sourceFile = new File(workDir, pattern);
                if (!sourceFile.exists()) {
                    logConsumer.accept("WARN", "归档文件不存在: " + pattern);
                    continue;
                }
                archiveSingleFile(sourceFile, appArchivePath, safeBranchName, logConsumer);
            }
        }
    }
    
    /**
     * 归档单个文件
     * <p>
     * 复制文件到归档目录，并将文件名中的版本号替换为分支/Tag名称。
     * 例如：app-2.0.9-SNAPSHOT.jar -> app-v2025.93.jar
     *
     * @param sourceFile  源文件
     * @param archivePath 归档目录
     * @param branchOrTag 分支或Tag名称
     * @param logConsumer 日志回调函数
     */
    private void archiveSingleFile(File sourceFile, File archivePath, String branchOrTag,
                                   java.util.function.BiConsumer<String, String> logConsumer) {
        String originalName = sourceFile.getName();
        String targetName = replaceVersionWithBranch(originalName, branchOrTag);
        
        File targetFile = new File(archivePath, targetName);
        
        try {
            Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logConsumer.accept("INFO", "归档成功: " + sourceFile.getName() + " -> " + targetFile.getAbsolutePath());
        } catch (IOException e) {
            logConsumer.accept("ERROR", "归档失败: " + sourceFile.getName() + ", 原因: " + e.getMessage());
        }
    }
    
    /**
     * 替换文件名中的版本号
     * <p>
     * 使用正则匹配版本号模式（如 2.0.9-SNAPSHOT），替换为分支/Tag名称。
     * 保留版本号之前的名称部分。
     *
     * @param fileName    原文件名
     * @param branchOrTag 分支或Tag名称
     * @return 替换后的文件名
     */
    private String replaceVersionWithBranch(String fileName, String branchOrTag) {
        String extension = "";
        String baseName = fileName;
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0) {
            extension = fileName.substring(lastDotIndex);
            baseName = fileName.substring(0, lastDotIndex);
        }
        
        // 匹配版本号模式：数字.数字.数字 开头，后面可能跟其他内容
        // 例如：2.0.9-SNAPSHOT, 1.0.0, 1.0.0.1-SNAPSHOT
        String versionPattern = "\\d+\\.\\d+\\.\\d+.*";
        
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(versionPattern);
        java.util.regex.Matcher matcher = pattern.matcher(baseName);
        
        if (matcher.find()) {
            // 获取版本号之前的部分（保留前缀）
            String prefix = baseName.substring(0, matcher.start());
            // 移除末尾的连接符（如 - 或 _）
            if (prefix.endsWith("-") || prefix.endsWith("_")) {
                prefix = prefix.substring(0, prefix.length() - 1);
            }
            return prefix + "-" + branchOrTag + extension;
        }
        
        // 如果没有匹配到版本号，在扩展名前添加分支名
        return baseName + "-" + branchOrTag + extension;
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
                appBuildService.updateStatus(appCode, "0", null);
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

    /**
     * 服务销毁时的清理操作
     * <p>
     * 停止所有正在运行的构建任务，优雅关闭线程池
     */
    @PreDestroy
    public void shutdown() {
        logger.info("正在关闭BuildTaskService线程池...");

        // 停止所有正在运行的构建进程
        for (Map.Entry<String, Process> entry : cmdMap.entrySet()) {
            try {
                logger.info("停止构建任务: {}", entry.getKey());
                entry.getValue().destroy();
            } catch (Exception e) {
                logger.error("停止构建任务失败: {}", entry.getKey(), e);
            }
        }
        cmdMap.clear();

        // 优雅关闭线程池
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
