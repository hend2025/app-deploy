package com.aeye.app.deploy;

import org.springframework.boot.SpringApplication;

/**
 * Spring Boot 应用部署管理系统启动类
 * <p>
 * 提供以下核心功能：
 * <ul>
 *   <li>版本构建管理 - 支持Git代码拉取、构建脚本执行、产物归档</li>
 *   <li>应用启动管理 - 支持JAR应用的启动、停止、状态监控</li>
 *   <li>实时日志查看 - 支持构建日志和运行日志的实时查看</li>
 * </ul>
 *
 * @author aeye
 * @since 1.0.0
 */
@org.springframework.boot.autoconfigure.SpringBootApplication
public class SpringBootApplication {

    /**
     * 应用程序入口
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(SpringBootApplication.class, args);
    }

}
