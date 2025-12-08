package com.aeye.app.deploy.util;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ProcessUtil {
    
    /**
     * 批量获取所有Java进程ID（按应用名称映射）
     * @return Map<应用名称, 进程ID>，如果未找到返回空Map
     */
    public static java.util.Map<String, String> getAllJarProcessIds() {
        java.util.Map<String, String> result = new java.util.HashMap<>();
        Process process = null;
        BufferedReader reader = null;
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder processBuilder;
            
            // 动态获取系统默认编码
            String charset = System.getProperty("file.encoding", "UTF-8");
            if (os.contains("win")) {
                // Windows系统使用wmic命令获取进程ID
                processBuilder = new ProcessBuilder("wmic", "process", "where", 
                    "name='java.exe'", "get", "processid,commandline");
                charset = "GBK"; // Windows中文系统使用GBK
            } else {
                // Linux/Unix系统使用ps命令获取进程ID
                processBuilder = new ProcessBuilder("ps", "-eo", "pid,cmd");
            }
            
            process = processBuilder.start();
            
            // 设置超时，避免进程挂起
            final Process finalProcess = process;
            java.util.concurrent.CompletableFuture<Void> timeoutFuture = java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(10000); // 10秒超时
                    if (finalProcess.isAlive()) {
                        finalProcess.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            
            reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), charset));
            
            String line;
            while ((line = reader.readLine()) != null) {
                // 跳过空行和标题行
                if (line.trim().isEmpty() || 
                    line.toLowerCase().contains("processid") || 
                    line.toLowerCase().contains("commandline") ||
                    line.toLowerCase().contains("pid") && line.toLowerCase().contains("cmd")) {
                    continue;
                }
                
                // 检查是否是Java进程
                String lowerLine = line.toLowerCase();
                if (lowerLine.contains("java") && lowerLine.contains(".jar")) {
                    // 提取进程ID
                    String pid = extractPid(line, os);
                    if (pid != null) {
                        // 尝试从命令行中提取jar文件名（应用名称）
                        // 查找.jar文件名
                        int jarIndex = lowerLine.indexOf(".jar");
                        if (jarIndex > 0) {
                            // 向前查找应用名称
                            int startIndex = Math.max(0, jarIndex - 50);
                            String jarPart = line.substring(startIndex, jarIndex + 4);
                            
                            // 提取jar文件名（去掉路径）
                            String jarFileName = extractJarFileName(jarPart);
                            if (jarFileName != null && !jarFileName.isEmpty()) {
                                // 移除.jar后缀作为应用名称
                                String appName = jarFileName.replace(".jar", "");
                                result.put(appName, pid);
                            }
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            System.err.println("批量获取进程ID失败: " + e.getMessage());
        } finally {
            // 确保资源被正确关闭
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    // 忽略关闭异常
                }
            }
            if (process != null) {
                try {
                    process.destroy();
                } catch (Exception e) {
                    // 忽略销毁异常
                }
            }
        }
        
        return result;
    }
    
    /**
     * 从字符串中提取jar文件名，并返回应用编码（去掉版本号后缀）
     */
    private static String extractJarFileName(String jarPart) {
        try {
            String jarFileName = null;
            
            // 查找最后一个路径分隔符后的文件名
            int lastSeparator = Math.max(jarPart.lastIndexOf('/'), jarPart.lastIndexOf('\\'));
            if (lastSeparator >= 0) {
                jarFileName = jarPart.substring(lastSeparator + 1);
            } else {
                // 如果没有路径分隔符，查找.jar前面的部分
                int jarIndex = jarPart.indexOf(".jar");
                if (jarIndex > 0) {
                    // 向前查找，直到遇到空格或特殊字符
                    int start = jarIndex;
                    while (start > 0 && !Character.isWhitespace(jarPart.charAt(start - 1)) 
                           && jarPart.charAt(start - 1) != '=' && jarPart.charAt(start - 1) != '\"') {
                        start--;
                    }
                    jarFileName = jarPart.substring(start, jarIndex + 4);
                }
            }
            
            if (jarFileName == null) {
                return null;
            }
            
            // 移除.jar后缀
            String appName = jarFileName.replace(".jar", "");
            
            // 注意：实际运行的jar文件名是 appCode.jar（不带版本号）
            // 所以这里直接返回去掉.jar后缀的文件名作为应用编码
            return appName;
        } catch (Exception e) {
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
     * 停止指定进程ID的进程
     * @param pid 进程ID
     * @return true表示成功，false表示失败
     */
    public static boolean killProcess(String pid) {
        if (pid == null || pid.trim().isEmpty()) {
            return false;
        }
        
        Process process = null;
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
            
            process = processBuilder.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
            
        } catch (Exception e) {
            System.err.println("停止进程失败: " + e.getMessage());
            return false;
        } finally {
            // 确保进程资源被正确释放
            if (process != null) {
                try {
                    process.destroy();
                } catch (Exception e) {
                    // 忽略销毁异常
                }
            }
        }
    }

}