package com.aeye.app.deploy.service;

import com.aeye.app.deploy.config.DirectoryConfig;
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

/**
 * 构建任务服务
 * 
 * 负责应用构建任务的执行和管理，包括：
 * - 启动构建任务（异步执行）
 * - 停止构建任务
 * - Git代码拉取
 * - 构建脚本执行
 * - 构建产物归档
 * 
 * 支持并发构建控制和构建日志实时输出
 *
 * @author aeye
 * @since 1.0.0
 */
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

    @Autowired
    private DirectoryConfig directoryConfig;

    private final Map<String, Process> cmdMap = new ConcurrentHashMap<>();

    @Value("${app.process.max-concurrent-builds:10}")
    private int maxConcurrentBuilds;

    private ExecutorService executorService;

    /**
     * 服务初始化
     * 创建构建任务线程池
     */
    @javax.annotation.PostConstruct
    public void init() {
        executorService = Executors.newFixedThreadPool(maxConcurrentBuilds, r -> {
            Thread thread = new Thread(r, "build-task-thread");
            thread.setDaemon(true);
            return thread;
        });
        logger.info("BuildTaskService初始化完成，最大并发构建数: {}", maxConcurrentBuilds);
    }

    /**
     * 判断当前操作系统是否为Windows
     *
     * @return true-Windows系统，false-其他系统
     */
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    /**
     * 创建临时构建脚本文件
     * Windows系统创建.cmd文件，Linux系统创建.sh文件
     * 自动处理换行符和编码问题
     *
     * @param appCode       应用编码
     * @param scriptContent 脚本内容
     * @param targetVersion 目标版本
     * @return 临时脚本文件
     * @throws IOException 文件创建失败时抛出
     */
    private File createTempScript(String appCode, String scriptContent, String targetVersion) throws IOException {
        String processedContent = scriptContent;

        String extension = isWindows() ? ".cmd" : ".sh";
        File scriptFile = File.createTempFile("build_" + appCode + "_", extension);

        if (isWindows()) {
            // Windows: 使用系统默认编码（通常是GBK），不强制UTF-8，防止Maven等工具出现乱码
            processedContent = processedContent.replace("\r\n", "\n").replace("\r", "\n");
            processedContent = processedContent.replace("\n", "\r\n");
            processedContent = "@echo off\r\n" + processedContent;

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
     * 
     * 异步执行构建流程：
     * 1. 拉取Git代码（如果配置了Git信息）
     * 2. 执行构建脚本
     * 3. 归档构建产物（如果配置了归档文件）
     *
     * @param appVersion  应用版本配置
     * @param branchOrTag 分支名或Tag名
     * @throws IllegalStateException 构建任务已在运行或并发数达到上限时抛出
     */
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
                            (level, msg) -> logBufferService.addLog(appCode, branchOrTag, level, msg, new Date()));
                } else {
                    workDir = new File(directoryConfig.getWorkspaceDir(), appCode).getAbsolutePath();
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
                    // Windows使用GBK编码读取，Linux使用UTF-8
                    java.nio.charset.Charset charset = isWindows() ? java.nio.charset.Charset.forName("GBK")
                            : StandardCharsets.UTF_8;
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(finalProcess.getInputStream(), charset))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            // 如果任务已被移除（已停止），则停止读取日志
                            if (!cmdMap.containsKey(appCode)) {
                                break;
                            }
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
                    logBufferService.addLog(appCode, branchOrTag, "INFO", "===== 步骤3: 归档文件 =====", new Date());
                    String archiveFilesConfig = appVersion.getArchiveFiles();
                    boolean hasArchiveConfig = archiveFilesConfig != null && !archiveFilesConfig.trim().isEmpty();
                    String appType = appVersion.getAppType();

                    if ("2".equals(appType)) {
                        // Vue前端项目：未配置归档文件时默认打包dist/目录为war文件，否则打包指定目录
                        String distDir = hasArchiveConfig ? archiveFilesConfig.trim() : "dist/";
                        logBufferService.addLog(appCode, branchOrTag, "INFO", "前端项目，打包目录: " + distDir, new Date());
                        archiveDistAsWar(appCode, branchOrTag, workDir, distDir,
                                (level, msg) -> logBufferService.addLog(appCode, branchOrTag, level, msg, new Date()));
                    } else {
                        // Java项目：未配置归档文件时默认为target/*，否则为指定文件
                        String archivePattern = hasArchiveConfig ? archiveFilesConfig : "target/*";
                        logBufferService.addLog(appCode, branchOrTag, "INFO", "Java项目，归档文件: " + archivePattern,
                                new Date());
                        archiveFiles(appCode, branchOrTag, workDir, archivePattern,
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

    /**
     * 将前端目录打包为war文件
     * 
     * 用于Vue前端项目，将指定目录打包为war文件，并复制目录到归档目录
     *
     * @param appCode     应用编码
     * @param branchOrTag 分支或Tag名称
     * @param workDir     工作目录
     * @param distPath    要打包的目录路径（相对于workDir）
     * @param logConsumer 日志回调函数
     */
    private void archiveDistAsWar(String appCode, String branchOrTag, String workDir, String distPath,
            java.util.function.BiConsumer<String, String> logConsumer) {
        // 处理目录路径，移除末尾的斜杠
        String cleanPath = distPath.replaceAll("[/\\\\]+$", "");
        File distDir = new File(workDir, cleanPath);
        if (!distDir.exists() || !distDir.isDirectory()) {
            logConsumer.accept("WARN", "未找到目录: " + distDir.getAbsolutePath());
            return;
        }

        // 创建归档目录
        File appArchivePath = new File(directoryConfig.getArchiveDir(), appCode);
        if (!appArchivePath.exists()) {
            appArchivePath.mkdirs();
        }

        // 清理分支/tag名称中的特殊字符
        String safeBranchName = branchOrTag.replaceAll("[/\\\\:*?\"<>|]", "_");

        // 获取目录名作为war文件名前缀（如 dist, his-h5 等）
        String dirName = distDir.getName();

        // 生成war文件名：目录名-版本号.war（如 dist-v2025.94.war）
        String warFileName = dirName + "-" + safeBranchName + ".war";
        File warFile = new File(appArchivePath, warFileName);

        logConsumer.accept("INFO", "开始打包dist目录为war文件: " + warFileName);

        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(
                new java.io.FileOutputStream(warFile), StandardCharsets.UTF_8)) {

            // 递归添加dist目录下的所有文件到war包
            addDirectoryToZip(distDir, "", zos, logConsumer);

            logConsumer.accept("INFO", "war文件打包成功: " + warFile.getAbsolutePath());
        } catch (IOException e) {
            logConsumer.accept("ERROR", "打包war文件失败: " + e.getMessage());
        }

        // 复制归档目录到应用归档目录下（不带版本号，如 his-h5）
        File targetDir = new File(appArchivePath, dirName);
        logConsumer.accept("INFO", "开始复制目录到归档目录: " + dirName);

        try {
            // 如果目标目录已存在，先删除
            if (targetDir.exists()) {
                logConsumer.accept("INFO", "目标目录已存在，先删除: " + targetDir.getAbsolutePath());
                deleteDirectory(targetDir);
            }

            copyDirectory(distDir, targetDir, logConsumer);
            logConsumer.accept("INFO", "目录复制成功: " + targetDir.getAbsolutePath());

            // Linux系统下授权755
            if (!isWindows()) {
                logConsumer.accept("INFO", "Linux系统，开始授权755...");
                setPermissions(targetDir, logConsumer);
                logConsumer.accept("INFO", "目录授权完成");
            }
        } catch (IOException e) {
            logConsumer.accept("ERROR", "复制目录失败: " + e.getMessage());
        }
    }

    /**
     * 递归删除目录
     *
     * @param dir 要删除的目录
     */
    private void deleteDirectory(File dir) throws IOException {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        Files.deleteIfExists(dir.toPath());
    }

    /**
     * 递归复制目录
     *
     * @param sourceDir   源目录
     * @param targetDir   目标目录
     * @param logConsumer 日志回调函数
     */
    private void copyDirectory(File sourceDir, File targetDir,
            java.util.function.BiConsumer<String, String> logConsumer) throws IOException {
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        File[] files = sourceDir.listFiles();
        if (files == null)
            return;

        for (File file : files) {
            File targetFile = new File(targetDir, file.getName());
            if (file.isDirectory()) {
                copyDirectory(file, targetFile, logConsumer);
            } else {
                Files.copy(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /**
     * 递归设置目录和文件权限为755
     * 仅在Linux系统下有效
     *
     * @param file        文件或目录
     * @param logConsumer 日志回调函数
     */
    private void setPermissions(File file, java.util.function.BiConsumer<String, String> logConsumer) {
        try {
            // 设置755权限：rwxr-xr-x
            java.nio.file.attribute.PosixFilePermission[] perms = {
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                    java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE,
                    java.nio.file.attribute.PosixFilePermission.GROUP_READ,
                    java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE,
                    java.nio.file.attribute.PosixFilePermission.OTHERS_READ,
                    java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE
            };
            Set<java.nio.file.attribute.PosixFilePermission> permSet = new HashSet<>(Arrays.asList(perms));

            Files.setPosixFilePermissions(file.toPath(), permSet);

            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        setPermissions(child, logConsumer);
                    }
                }
            }
        } catch (UnsupportedOperationException e) {
            // Windows系统不支持POSIX权限，忽略
        } catch (IOException e) {
            logConsumer.accept("WARN", "设置权限失败: " + file.getAbsolutePath() + ", 原因: " + e.getMessage());
        }
    }

    /**
     * 递归将目录添加到zip文件
     *
     * @param dir         要添加的目录
     * @param basePath    zip内的基础路径
     * @param zos         zip输出流
     * @param logConsumer 日志回调函数
     */
    private void addDirectoryToZip(File dir, String basePath, java.util.zip.ZipOutputStream zos,
            java.util.function.BiConsumer<String, String> logConsumer) throws IOException {
        File[] files = dir.listFiles();
        if (files == null)
            return;

        for (File file : files) {
            String entryName = basePath.isEmpty() ? file.getName() : basePath + "/" + file.getName();

            if (file.isDirectory()) {
                // 递归处理子目录
                addDirectoryToZip(file, entryName, zos, logConsumer);
            } else {
                // 添加文件到zip
                java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(entryName);
                zos.putNextEntry(entry);
                Files.copy(file.toPath(), zos);
                zos.closeEntry();
            }
        }
    }

    /**
     * 归档构建产物
     * 
     * 支持glob模式匹配文件，将匹配的文件复制到归档目录
     * 归档目录结构：archiveDir/appCode/文件名
     *
     * @param appCode      应用编码
     * @param branchOrTag  分支或Tag名称（用于文件名替换）
     * @param workDir      工作目录
     * @param archiveFiles 归档文件配置（逗号分隔，支持glob模式）
     * @param logConsumer  日志回调函数
     */
    private void archiveFiles(String appCode, String branchOrTag, String workDir, String archiveFiles,
            java.util.function.BiConsumer<String, String> logConsumer) {
        // 创建归档目录：archiveDir/appCode/
        File appArchivePath = new File(directoryConfig.getArchiveDir(), appCode);
        if (!appArchivePath.exists()) {
            appArchivePath.mkdirs();
        }

        // 清理分支/tag名称中的特殊字符
        String safeBranchName = branchOrTag.replaceAll("[/\\\\:*?\"<>|]", "_");

        String[] patterns = archiveFiles.split(",");
        for (String pattern : patterns) {
            pattern = pattern.trim();
            if (pattern.isEmpty())
                continue;

            // 检查是否为glob模式
            if (pattern.contains("*") || pattern.contains("?")) {
                // 使用glob模式匹配文件
                try {
                    Path workPath = java.nio.file.Paths.get(workDir);
                    String globPattern = "glob:" + pattern;
                    java.nio.file.PathMatcher matcher = java.nio.file.FileSystems.getDefault()
                            .getPathMatcher(globPattern);

                    List<Path> matchedFiles = new ArrayList<>();
                    Files.walkFileTree(workPath, new java.nio.file.SimpleFileVisitor<Path>() {
                        @Override
                        public java.nio.file.FileVisitResult visitFile(Path file,
                                java.nio.file.attribute.BasicFileAttributes attrs) {
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
     * 
     * 复制文件到归档目录，并将文件名中的版本号替换为分支/Tag名称
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
     * 
     * 使用正则匹配版本号模式（如 2.0.9-SNAPSHOT），替换为分支/Tag名称
     * 保留版本号之前的名称部分
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
     * 根据日志内容中的关键字判断日志级别
     *
     * @param logContent 日志内容
     * @return 日志级别：ERROR/WARN/DEBUG/INFO
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
     *
     * @param appCode 应用编码
     * @return true-停止成功，false-停止失败或任务不存在
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
