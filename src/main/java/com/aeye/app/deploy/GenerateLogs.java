package com.aeye.app.deploy;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

public class GenerateLogs {
    private static final Random random = new Random();
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    // 日志级别
    private static final String[] LOG_LEVELS = {"INFO", "DEBUG", "WARN", "ERROR"};
    
    // 日志消息模板
    private static final String[] LOG_MESSAGES = {
        "User login successful",
        "Database connection established",
        "Cache update completed",
        "File upload processing",
        "API request processing completed",
        "Scheduled task execution started",
        "Scheduled task execution completed",
        "Permission verification passed",
        "Permission verification failed",
        "System resource monitoring",
        "Memory usage check",
        "CPU usage monitoring",
        "Network connection status check",
        "Configuration file loaded",
        "Service startup completed",
        "Service shutdown completed",
        "Error handling completed",
        "Log cleanup task",
        "Backup task execution",
        "Data synchronization completed"
    };
    
    // 调试日志消息模板
    private static final String[] DEBUG_MESSAGES = {
        "Debug: Processing request parameters",
        "Debug: Database query execution",
        "Debug: Cache hit/miss analysis",
        "Debug: Method entry point",
        "Debug: Method exit point",
        "Debug: Variable state check",
        "Debug: Loop iteration count",
        "Debug: Conditional branch taken",
        "Debug: Exception stack trace",
        "Debug: Memory allocation",
        "Debug: Garbage collection",
        "Debug: Thread synchronization",
        "Debug: Network packet analysis",
        "Debug: File I/O operation",
        "Debug: Configuration validation",
        "Debug: Authentication flow",
        "Debug: Authorization check",
        "Debug: Data transformation",
        "Debug: Performance metrics",
        "Debug: System state snapshot"
    };
    
    public static void main(String[] args) {
        // 获取日志文件路径
        String applicationLogPath = "/home/logs/application.log";
        String debugLogPath = "/home/logs/debug.log";
        
        // 确保日志目录存在
        File logDir = new File("/home/logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        
        // 主循环：每秒生成日志
        while (true) {
            try {
                // 生成应用日志（每秒1-3行）
                generateLogs(applicationLogPath, 1, 3);
                
                // 生成调试日志（每秒3-5行）
                generateDebugLogs(debugLogPath, 3, 5);
                
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                // 静默处理异常
            }
        }
    }
    
    /**
     * 生成日志记录
     */
    private static void generateLogs(String logFilePath, int minLines, int maxLines) {
        try {
            int lineCount = minLines + random.nextInt(maxLines - minLines + 1);
            
            try (FileWriter writer = new FileWriter(logFilePath, true)) {
                for (int i = 0; i < lineCount; i++) {
                    String logLine = generateLogLine();
                    writer.write(logLine + "\n");
                }
                writer.flush();
            }
            
        } catch (IOException e) {
            // 静默处理IO异常
        }
    }
    
    /**
     * 生成调试日志记录
     */
    private static void generateDebugLogs(String logFilePath, int minLines, int maxLines) {
        try {
            int lineCount = minLines + random.nextInt(maxLines - minLines + 1);
            
            try (FileWriter writer = new FileWriter(logFilePath, true)) {
                for (int i = 0; i < lineCount; i++) {
                    String logLine = generateDebugLogLine();
                    writer.write(logLine + "\n");
                }
                writer.flush();
            }
            
        } catch (IOException e) {
            // 静默处理IO异常
        }
    }
    
    /**
     * 生成单行日志
     */
    private static String generateLogLine() {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(formatter);
        String logLevel = LOG_LEVELS[random.nextInt(LOG_LEVELS.length)];
        String message = LOG_MESSAGES[random.nextInt(LOG_MESSAGES.length)];
        
        // 生成线程名称
        String threadName = "thread-" + (1 + random.nextInt(10));
        
        // 生成类名
        String className = "com.example.springboothtmljquery.service.LogService";
        
        return String.format("%s %s [%s] %s - %s", 
                timestamp, logLevel, threadName, className, message);
    }
    
    /**
     * 生成单行调试日志
     */
    private static String generateDebugLogLine() {
        LocalDateTime now = LocalDateTime.now();
        String timestamp = now.format(formatter);
        String message = DEBUG_MESSAGES[random.nextInt(DEBUG_MESSAGES.length)];
        
        // 生成线程名称
        String threadName = "debug-thread-" + (1 + random.nextInt(5));
        
        // 生成类名
        String className = "com.example.springboothtmljquery.debug.DebugService";
        
        // 生成方法名
        String methodName = "debugMethod" + (1 + random.nextInt(10));
        
        // 生成行号
        int lineNumber = 100 + random.nextInt(500);
        
        return String.format("%s DEBUG [%s] %s.%s:%d - %s", 
                timestamp, threadName, className, methodName, lineNumber, message);
    }
}
