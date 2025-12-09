package com.aeye.app.deploy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.BiConsumer;

@Service
public class GitService {

    private static final Logger logger = LoggerFactory.getLogger(GitService.class);

    @Value("${app.directory.workspace}")
    private String workspaceDir;

    /**
     * 拉取指定分支或tag的代码
     * @param appCode 应用编码
     * @param gitUrl Git仓库地址
     * @param gitAcct Git账号
     * @param gitPwd Git密码
     * @param branchOrTag 分支或tag名称
     * @param logConsumer 日志回调
     * @return 工作目录路径
     */
    public String cloneOrPull(String appCode, String gitUrl, String gitAcct, String gitPwd, 
                              String branchOrTag, BiConsumer<String, String> logConsumer) throws Exception {
        
        // 构建带认证的Git URL
        String authUrl = buildAuthUrl(gitUrl, gitAcct, gitPwd);
        
        // 工作目录：workspace/appCode
        File workDir = new File(workspaceDir, appCode);
        
        if (workDir.exists() && new File(workDir, ".git").exists()) {
            // 已存在仓库，执行fetch和checkout
            logConsumer.accept("INFO", "检测到已有仓库，执行更新操作...");
            executeGitCommand(workDir, new String[]{"git", "fetch", "--all", "--tags"}, logConsumer);
            executeGitCommand(workDir, new String[]{"git", "checkout", branchOrTag}, logConsumer);
            executeGitCommand(workDir, new String[]{"git", "pull", "origin", branchOrTag}, logConsumer);
        } else {
            // 不存在仓库，执行clone
            logConsumer.accept("INFO", "开始克隆仓库: " + gitUrl);
            workDir.mkdirs();
            executeGitCommand(workDir.getParentFile(), new String[]{"git", "clone", "-b", branchOrTag, authUrl, appCode}, logConsumer);
        }
        
        logConsumer.accept("INFO", "代码拉取完成，工作目录: " + workDir.getAbsolutePath());
        return workDir.getAbsolutePath();
    }

    /**
     * 构建带认证的Git URL
     */
    private String buildAuthUrl(String gitUrl, String gitAcct, String gitPwd) {
        if (gitAcct == null || gitAcct.isEmpty() || gitPwd == null || gitPwd.isEmpty()) {
            return gitUrl;
        }
        
        // 处理 https://xxx 格式
        if (gitUrl.startsWith("https://")) {
            return "https://" + encodeCredential(gitAcct) + ":" + encodeCredential(gitPwd) + "@" + gitUrl.substring(8);
        }
        // 处理 http://xxx 格式
        if (gitUrl.startsWith("http://")) {
            return "http://" + encodeCredential(gitAcct) + ":" + encodeCredential(gitPwd) + "@" + gitUrl.substring(7);
        }
        return gitUrl;
    }

    /**
     * URL编码凭证中的特殊字符
     */
    private String encodeCredential(String credential) {
        try {
            return java.net.URLEncoder.encode(credential, "UTF-8");
        } catch (Exception e) {
            return credential;
        }
    }

    /**
     * 执行Git命令
     */
    private void executeGitCommand(File workDir, String[] command, BiConsumer<String, String> logConsumer) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir);
        pb.redirectErrorStream(true);
        
        // 隐藏密码信息的日志
        String cmdLog = String.join(" ", command).replaceAll(":[^@]+@", ":****@");
        logConsumer.accept("INFO", "执行命令: " + cmdLog);
        
        Process process = pb.start();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 过滤掉包含密码的行
                String safeLine = line.replaceAll(":[^@]+@", ":****@");
                logConsumer.accept("INFO", safeLine);
            }
        }
        
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Git命令执行失败，退出码: " + exitCode);
        }
    }

}
