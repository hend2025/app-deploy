package com.aeye.app.deploy.service;

import com.aeye.app.deploy.config.DirectoryConfig;
import com.aeye.app.deploy.model.AppDeploy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * JAR 应用进程管理服务
 * 
 * 提供 JAR 应用的启动功能，包括：
 * - 从归档目录查找指定版本的 JAR 文件
 * - 复制 JAR 到运行目录
 * - 异步启动 Java 进程
 * - 实时采集进程输出日志
 * 
 * 支持多种目录结构：
 * - archive/appCode/svcCode-version.jar
 * - archive/svcCode/svcCode-version.jar
 * - archive/svcCode-version.jar（兼容旧结构）
 */
@Service
public class JarProcessService {
    
    private static final Logger logger = LoggerFactory.getLogger(JarProcessService.class);
    
    @Autowired
    private DirectoryConfig directoryConfig;
    
    /** 最大并发启动任务数，防止资源耗尽 */
    @Value("${app.process.max-concurrent-startups:10}")
    private int maxConcurrentStartups;
    
    @Autowired
    private AppDeployService appDeployService;
    
    @Autowired
    private LogBufferService logBufferService;

    /** 操作系统名称 */
    private static final String OS = System.getProperty("os.name").toLowerCase();
    
    /** 是否为Windows系统 */
    private static final boolean IS_WINDOWS = OS.contains("win");
    
    /** 启动任务线程池 */
    private ExecutorService executorService;
    
    @javax.annotation.PostConstruct
    public void init() {
        executorService = Executors.newFixedThreadPool(maxConcurrentStartups, r -> {
            Thread thread = new Thread(r, "jar-startup-thread");
            thread.setDaemon(true);
            return thread;
        });
        logger.info("JarProcessService初始化完成，最大并发启动数: {}", maxConcurrentStartups);
    }

    /**
     * 启动 JAR 应用
     * 
     * 执行流程：
     * 1. 按优先级查找 JAR 文件（appCode目录 - svcCode目录 - 根目录）
     * 2. 复制指定版本 JAR 到运行位置（svcCode.jar）
     * 3. 构建启动命令（Java + JVM参数 + JAR）
     * 4. 异步启动进程并采集日志
     *
     * @param appDeploy 应用部署信息
     * @param version   版本号
     * @param params    JVM 启动参数（多行文本，每行一个参数）
     * @throws Exception 如果 JAR 文件不存在或启动失败
     */
    public void startJarApp(AppDeploy appDeploy, String version, String params) throws Exception {
        String svcCode = appDeploy.getSvcCode();
        String appCode = appDeploy.getAppCode();
        
        String sourceJarPath = null;
        String targetJarPath = null;
        File file = null;
        
        String jarDir = directoryConfig.getArchiveDir();
        
        // 优先读取app_code目录下的jar（如果appCode不为空）
        if (appCode != null && !appCode.trim().isEmpty()) {
            String appCodeDir = jarDir + File.separator + appCode;
            sourceJarPath = appCodeDir + File.separator + svcCode + "-" + version + ".jar";
            file = new File(sourceJarPath);
            if (file.exists()) {
                targetJarPath = appCodeDir + File.separator + svcCode + ".jar";
            }
        }
        
        // 如果app_code目录下找不到，尝试svcCode目录
        if (file == null || !file.exists()) {
            String svcCodeDir = jarDir + File.separator + svcCode;
            sourceJarPath = svcCodeDir + File.separator + svcCode + "-" + version + ".jar";
            file = new File(sourceJarPath);
            if (file.exists()) {
                targetJarPath = svcCodeDir + File.separator + svcCode + ".jar";
            }
        }
        
        // 兼容旧目录结构（archiveDir/xxx.jar）
        if (file == null || !file.exists()) {
            sourceJarPath = jarDir + File.separator + svcCode + "-" + version + ".jar";
            file = new File(sourceJarPath);
            if (file.exists()) {
                targetJarPath = jarDir + File.separator + svcCode + ".jar";
            }
        }

        if (file == null || !file.exists()) {
            throw new RuntimeException("JAR文件不存在【" + sourceJarPath + "】");
        }

        Path source = Paths.get(sourceJarPath);
        Path target = Paths.get(targetJarPath);

        // 确保目标目录存在
        File targetDir = new File(targetJarPath).getParentFile();
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        // 如果目标文件已存在，则替换
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        
        // 用于lambda表达式的final变量
        final String finalJarFilePath = targetJarPath;
        final String finalWorkDir = new File(targetJarPath).getParent();
        final String finalSvcCode = svcCode;

        // 开始新的运行会话（递增运行次数），使用svcCode作为日志标识（与构建日志保持一致）
        logBufferService.startNewSession(finalSvcCode, version);

        executorService.submit(() -> {
            try {
                logger.info("开始启动应用: {}, 版本: {}", finalSvcCode, version);

                // 构建启动命令
                ProcessBuilder processBuilder = new ProcessBuilder();
                
                // 解析启动参数
                String[] jvmArgs = null;
                if (params != null && !params.trim().isEmpty()) {
                    jvmArgs = params.split("[\\r\\n]+");
                }
                
                // 构建完整的命令
                java.util.List<String> command = new java.util.ArrayList<>();
                
                // 添加Java命令
                String javaCmd = getJavaCommand();
                command.add(javaCmd);
                
                // 添加JVM参数
                if (jvmArgs != null) {
                    for (String arg : jvmArgs) {
                        String trimmedArg = arg.trim();
                        if (!trimmedArg.isEmpty()) {
                            command.add(trimmedArg);
                        }
                    }
                }
                
                // 添加jar文件
                command.add("-jar");
                command.add(finalJarFilePath);
                
                processBuilder.command(command);
                processBuilder.directory(new File(finalWorkDir));
                
                // 合并标准输出和错误输出
                processBuilder.redirectErrorStream(true);
                
                // 启动进程
                Process process = processBuilder.start();

                // 更新应用信息
                appDeploy.setParams(params);
                appDeploy.setVersion(version);
                appDeployService.saveApp(appDeploy);
                
                logger.info("应用启动命令已执行: {}", finalSvcCode);
                
                // 异步读取进程输出并写入内存缓冲，使用svcCode作为日志标识（与构建日志保持一致）
                readProcessOutput(process, finalSvcCode, version);

            } catch (Exception e) {
                logger.error("启动应用失败: {}, 版本: {}", finalSvcCode, version, e);
                logBufferService.addLog(finalSvcCode, version, "ERROR", "启动应用失败: " + e.getMessage(), new Date());
            }
        });

    }
    
    /**
     * 读取进程输出并写入内存缓冲
     * 
     * 使用非守护线程确保进程结束后日志能完整读取。
     * 日志会实时写入 LogBufferService，支持前端实时查看。
     *
     * @param process 进程对象
     * @param appCode 应用编码
     * @param version 版本号
     */
    private void readProcessOutput(Process process, String appCode, String version) {
        Thread outputReader = new Thread(() -> {
            // Java应用输出通常是UTF-8，使用UTF-8编码读取
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logBufferService.addLog(appCode, version, parseLogLevel(line), line, new Date());
                }
            } catch (Exception e) {
                // 进程被终止时会抛出异常，这是正常的
                if (process.isAlive()) {
                    logger.error("读取进程输出失败: {}", appCode, e);
                }
            } finally {
                logger.debug("进程输出读取线程结束: {}", appCode);
            }
        }, "log-reader-" + appCode);
        // 使用非守护线程，确保日志能完整读取
        outputReader.setDaemon(false);
        outputReader.start();
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
        }
        return "INFO";
    }
    
    /**
     * 获取Java命令（跨平台）
     */
    private String getJavaCommand() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
            if (IS_WINDOWS) {
                javaBin += ".exe";
            }
            File javaFile = new File(javaBin);
            if (javaFile.exists()) {
                return javaBin;
            }
        }
        // 如果找不到完整路径，返回"java"命令，依赖PATH环境变量
        return IS_WINDOWS ? "java.exe" : "java";
    }
    
    /**
     * 服务销毁时的清理操作
     * 
     * 优雅关闭线程池，最多等待 10 秒让正在执行的任务完成。
     * 超时后强制关闭线程池。
     */
    @PreDestroy
    public void shutdown() {
        logger.info("正在关闭JarProcessService线程池...");
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
        logger.info("JarProcessService线程池已关闭");
    }

}
