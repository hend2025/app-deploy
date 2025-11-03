package com.aeye.app.deploy.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ProcessUtil {
    
    /**
     * 获取指定名称的Java进程ID
     * @param appName 应用名称（jar文件名）
     * @return 进程ID，如果未找到返回null
     */
    public static String getJavaProcessId(String appName) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder processBuilder;
            
            if (os.contains("win")) {
                // Windows系统使用wmic命令获取进程ID
                processBuilder = new ProcessBuilder("wmic", "process", "where", 
                    "name='java.exe'", "get", "processid,commandline");
            } else {
                // Linux/Unix系统使用ps命令获取进程ID
                processBuilder = new ProcessBuilder("ps", "-eo", "pid,cmd");
            }
            
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), "GBK"));
            
            String line;
            while ((line = reader.readLine()) != null) {
                // 检查命令行中是否包含应用名称
                if (line.toLowerCase().contains(appName.toLowerCase()) && 
                    line.toLowerCase().contains("java")) {
                    
                    // 提取进程ID
                    String pid = extractPid(line, os);
                    reader.close();
                    process.destroy();
                    return pid;
                }
            }
            
            reader.close();
            process.waitFor();
            return null;
            
        } catch (Exception e) {
            System.err.println("获取进程ID失败: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * 从命令行输出中提取进程ID
     */
    private static String extractPid(String line, String os) {
        try {
            if (os.contains("win")) {
                // Windows: 输出格式为 "CommandLine  ProcessId"
                String[] parts = line.trim().split("\\s+");
                // 最后一个字段是ProcessId
                return parts[parts.length - 1];
            } else {
                // Linux: 输出格式为 "PID CMD"
                String[] parts = line.trim().split("\\s+", 2);
                return parts[0];
            }
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 获取指定jar文件的Java进程ID
     * @param jarFileName jar文件名
     * @return 进程ID，如果未找到返回null
     */
    public static String getJarProcessId(String jarFileName) {
        return getJavaProcessId(jarFileName);
    }
    
    /**
     * 停止指定进程ID的进程
     * @param pid 进程ID
     * @return true表示成功，false表示失败
     */
    public static boolean killProcess(String pid) {
        if (pid == null || pid.trim().isEmpty()) {
            return false;
        }
        
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder processBuilder;
            
            if (os.contains("win")) {
                // Windows系统使用taskkill命令
                processBuilder = new ProcessBuilder("taskkill", "/F", "/PID", pid);
            } else {
                // Linux/Unix系统使用kill命令
                processBuilder = new ProcessBuilder("kill", "-9", pid);
            }
            
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
            
        } catch (Exception e) {
            System.err.println("停止进程失败: " + e.getMessage());
            return false;
        }
    }

}
