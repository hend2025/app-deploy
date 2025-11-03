package com.aeye.app.deploy.service;

import com.aeye.app.deploy.model.VerInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BuildTaskService {
    
    @Value("${app.directory.logs:/home/logs}")
    private String logsDir;
    @Autowired
    private VerMgtService VerMgtService;

    private final Map<String, Process> cmdMap = new ConcurrentHashMap<>();

    /**
     * 启动构建任务
     */
    public void startBuild(VerInfo appVersion, String targetVersion) {
        Thread buildThread = new Thread(() -> {
            Process process = null;
            try {
                // 创建日志文件路径：应用名称_版本号_build_时间.log
                String logFileName = String.format("build_%s_%s_%s.log",
                    appVersion.getAppCode(),
                    targetVersion,
                    new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()));
                String logFilePath = logsDir + "/" + logFileName;
                appVersion.setLogFile(logFilePath);
                
                // 确保日志目录存在
                File logDir = new File(logsDir);
                if (!logDir.exists()) {
                    logDir.mkdirs();
                }
                
                // 执行构建脚本
                String scriptPath = appVersion.getScript();
                ProcessBuilder processBuilder = new ProcessBuilder();
                if (scriptPath.endsWith(".cmd") || scriptPath.endsWith(".bat")) {
                    processBuilder.command("cmd", "/c", "chcp 65001 >nul && " + scriptPath, targetVersion);
                } else if (scriptPath.endsWith(".sh")) {
                    processBuilder.command("bash", scriptPath, targetVersion);
                } else {
                    processBuilder.command(scriptPath, targetVersion);
                }
                
                processBuilder.directory(new File(scriptPath).getParentFile());
                processBuilder.redirectOutput(new File(logFilePath));
                processBuilder.redirectError(new File(logFilePath));
                
                process = processBuilder.start();

                VerMgtService.updateStatus(appVersion.getAppCode(),"1",null);
                cmdMap.put(appVersion.getAppCode(),process);

                int exitCode = process.waitFor();

                // 更新任务状态
                if (exitCode == 0) {
                    VerMgtService.updateStatus(appVersion.getAppCode(),"0",targetVersion);
                } else {
                    VerMgtService.updateStatus(appVersion.getAppCode(),"0",null);
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                cmdMap.remove(appVersion.getAppCode());
                if (process != null) {
                    process.destroy();
                }
            }
        });

        buildThread.start();

    }
    
    /**
     * 停止构建任务
     */
    public boolean stopBuild(String appCode) {
        Process process = cmdMap.get(appCode);
        if (process != null) {
            cmdMap.remove(appCode);
            process.destroy();
        }
        return true;
    }

}
