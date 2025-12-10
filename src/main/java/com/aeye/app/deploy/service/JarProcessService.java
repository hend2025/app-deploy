package com.aeye.app.deploy.service;

import com.aeye.app.deploy.model.AppInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.aeye.app.deploy.service.LogFileWriterService.LOG_TYPE_CONSOLE;

@Service
public class JarProcessService {
    
    private static final Logger logger = LoggerFactory.getLogger(JarProcessService.class);
    
    /** JAR文件存放目录 */
    @Value("${app.directory.archive:/home/archive}")
    private String jarDir;
    
    @Autowired
    private AppMgtService appMgtService;
    
    @Autowired
    private LogBufferService logBufferService;

    /** 操作系统名称 */
    private static final String OS = System.getProperty("os.name").toLowerCase();
    
    /** 是否为Windows系统 */
    private static final boolean IS_WINDOWS = OS.contains("win");
    
    /** 最大并发启动任务数 */
    private static final int MAX_CONCURRENT_STARTUPS = 10;
    
    /** 启动任务线程池 */
    private final ExecutorService executorService = Executors.newFixedThreadPool(MAX_CONCURRENT_STARTUPS, r -> {
        Thread thread = new Thread(r, "jar-startup-thread");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * 启动JAR应用
     * <p>
     * 执行流程：
     * <ol>
     *   <li>复制指定版本JAR到运行位置（appCode.jar）</li>
     *   <li>构建启动命令（Java + JVM参数 + JAR）</li>
     *   <li>异步启动进程并采集日志</li>
     * </ol>
     *
     * @param appCode 应用编码
     * @param version 版本号
     * @param params  JVM启动参数（多行，每行一个参数）
     * @throws Exception 如果JAR文件不存在或启动失败
     */
    public void startJarApp(String appCode, String version, String params) throws Exception {
        // 构建JAR文件路径
        String jarFilePath = jarDir + File.separator + appCode + ".jar";
        String jarFilePath2 = jarDir + File.separator + appCode + "-" + version + ".jar";
        File file = new File(jarFilePath2);

        if (!file.exists()) {
            throw new RuntimeException("JAR文件不存在【" + jarFilePath2+"】");
        }

        Path source = Paths.get(jarFilePath2);
        Path target = Paths.get(jarFilePath);

        // 如果目标文件已存在，则替换
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

        // 开始新的运行会话（递增运行次数）
        logBufferService.startNewSession(appCode, LogFileWriterService.LOG_TYPE_CONSOLE, version);

        executorService.submit(() -> {
            try {
                logger.info("开始启动应用: {}, 版本: {}", appCode, version);

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
                command.add(jarFilePath);
                
                processBuilder.command(command);
                processBuilder.directory(new File(jarDir));
                
                // 合并标准输出和错误输出
                processBuilder.redirectErrorStream(true);
                
                // 启动进程
                Process process = processBuilder.start();

                // 更新应用信息
                AppInfo appInfo = appMgtService.getAppByCode(appCode);
                if (appInfo != null) {
                    appInfo.setParams(params);
                    appInfo.setVersion(version);
                    appMgtService.saveApp(appInfo);
                } else {
                    logger.warn("应用信息不存在，无法更新: {}", appCode);
                }
                
                logger.info("应用启动命令已执行: {}", appCode);
                
                // 异步读取进程输出并写入内存缓冲
                readProcessOutput(process, appCode, version);

            } catch (Exception e) {
                logger.error("启动应用失败: {}, 版本: {}", appCode, version, e);
                logBufferService.addLog(appCode, version, "ERROR", "启动应用失败: " + e.getMessage(), new Date(),
                        LogFileWriterService.LOG_TYPE_CONSOLE);
            }
        });

    }
    
    /**
     * 读取进程输出并写入内存缓冲
     */
    private void readProcessOutput(Process process, String appCode, String version) {
        Thread outputReader = new Thread(() -> {
            // Java应用输出通常是UTF-8，使用UTF-8编码读取
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logBufferService.addLog(appCode, version, parseLogLevel(line), line, new Date(), 
                            LogFileWriterService.LOG_TYPE_CONSOLE);
                }
            } catch (Exception e) {
                logger.error("读取进程输出失败: {}", appCode, e);
            }
        }, "log-reader-" + appCode);
        outputReader.setDaemon(true);
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
     * <p>
     * 优雅关闭线程池，等待正在执行的启动任务完成
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
