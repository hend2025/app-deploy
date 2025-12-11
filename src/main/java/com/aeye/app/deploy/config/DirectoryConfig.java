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
 * 统一管理 workspace、archive、logs 目录，并在启动时自动创建
 */
@Configuration
public class DirectoryConfig {

    private static final Logger logger = LoggerFactory.getLogger(DirectoryConfig.class);

    @Value("${app.home-directory:/home/aeye}")
    private String homeDirectory;

    private String workspaceDir;
    private String archiveDir;
    private String logsDir;

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

    public String getHomeDirectory() {
        return homeDirectory;
    }

    public String getWorkspaceDir() {
        return workspaceDir;
    }

    public String getArchiveDir() {
        return archiveDir;
    }

    public String getLogsDir() {
        return logsDir;
    }
}
