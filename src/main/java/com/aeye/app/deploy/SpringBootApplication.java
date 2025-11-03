package com.aeye.app.deploy;

import com.aeye.app.deploy.service.LogCleanupService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;

@org.springframework.boot.autoconfigure.SpringBootApplication
public class SpringBootApplication {

    @Autowired
    private LogCleanupService logCleanupService;

    public static void main(String[] args) {
        SpringApplication.run(SpringBootApplication.class, args);
    }

    /**
     * 应用关闭事件
     */
    @EventListener(ContextClosedEvent.class)
    public void onApplicationClosed() {
        System.out.println("应用正在关闭，清理日志清理服务资源...");
        if (logCleanupService != null) {
            logCleanupService.shutdown();
        }
    }

}
