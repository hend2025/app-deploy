package com.aeye.app.deploy.config;

import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 目录配置类
 * 
 * 统一管理应用的工作目录配置，包括：
 * - homeDirectory: 应用主目录，所有子目录的根路径
 * - workspaceDir: 代码工作空间目录，用于存放 Git 克隆的代码
 * - archiveDir: 归档目录，用于存放构建产物（JAR包等）
 * - logsDir: 日志目录，用于存放应用运行日志
 * 
 * 应用启动时会自动创建这些目录（如果不存在）。
 * 
 * 配置示例（application.yml）：
 * app:
 *   home-directory: /home/aeye
 */
@Configuration
public class DirectoryConfig {

    private static final Logger logger = LoggerFactory.getLogger(DirectoryConfig.class);

    /** 
     * 应用主目录路径
     * 从配置文件读取，默认值为 /home/aeye
     */
    @Value("${app.home-directory:/home/aeye}")
    private String homeDirectory;

    /** 代码工作空间目录路径 */
    private String workspaceDir;
    
    /** 构建产物归档目录路径 */
    private String archiveDir;
    
    /** 应用日志目录路径 */
    private String logsDir;

    /**
     * 初始化目录配置
     * 
     * 在 Spring 容器初始化完成后执行，完成以下操作：
     * 1. 创建主目录（如果不存在）
     * 2. 构建并创建子目录路径
     * 3. 输出初始化日志
     */
    @PostConstruct
    public void init() {
        // 先创建 home 目录
        createDirectoryIfNotExists(homeDirectory);
        
        // 构建子目录路径
        this.workspaceDir = Paths.get(homeDirectory, "workspace").toString();
        this.archiveDir = Paths.get(homeDirectory, "archive").toString();
        this.logsDir = Paths.get(homeDirectory, "logs").toString();

        // 自动创建子目录
        createDirectoryIfNotExists(workspaceDir);
        createDirectoryIfNotExists(archiveDir);
        createDirectoryIfNotExists(logsDir);

        logger.info("目录配置初始化完成 - homeDirectory: {}", homeDirectory);
        logger.info("  workspace: {}", workspaceDir);
        logger.info("  archive: {}", archiveDir);
        logger.info("  logs: {}", logsDir);
    }

    /**
     * 创建目录（如果不存在）
     * 
     * 使用 Files.createDirectories 递归创建目录，
     * 如果目录已存在则不做任何操作。
     *
     * @param dir 要创建的目录路径
     */
    private void createDirectoryIfNotExists(String dir) {
        Path path = Paths.get(dir);
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
                logger.info("创建目录: {}", dir);
            } catch (IOException e) {
                logger.error("创建目录失败: {}", dir, e);
            }
        }
    }

    /**
     * 获取应用主目录路径
     *
     * @return 主目录绝对路径
     */
    public String getHomeDirectory() {
        return homeDirectory;
    }

    /**
     * 获取代码工作空间目录路径
     * 
     * 用于存放从 Git 仓库克隆的代码。
     *
     * @return 工作空间目录绝对路径
     */
    public String getWorkspaceDir() {
        return workspaceDir;
    }

    /**
     * 获取构建产物归档目录路径
     * 
     * 用于存放构建完成的 JAR 包等产物。
     *
     * @return 归档目录绝对路径
     */
    public String getArchiveDir() {
        return archiveDir;
    }

    /**
     * 获取应用日志目录路径
     * 
     * 用于存放应用运行时产生的日志文件。
     *
     * @return 日志目录绝对路径
     */
    public String getLogsDir() {
        return logsDir;
    }
}
