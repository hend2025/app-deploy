package com.aeye.app.deploy.service;

import com.aeye.app.deploy.config.DirectoryConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.BiConsumer;


/**
 * Git 代码管理服务
 * 
 * 提供 Git 仓库的克隆和更新功能，支持：
 * - HTTP/HTTPS 协议的 Git 仓库
 * - 用户名密码认证
 * - 分支和 Tag 的切换
 * 
 * 安全特性：
 * - 分支/Tag 名称校验，防止命令注入
 * - 日志中自动隐藏密码信息
 */
@Service
public class GitService {

    @Autowired
    private DirectoryConfig directoryConfig;

    /**
     * 拉取指定分支或 Tag 的代码
     * 
     * 执行流程：
     * 1. 验证分支/Tag 名称合法性
     * 2. 构建带认证信息的 Git URL
     * 3. 如果本地已有仓库，执行 fetch + checkout（分支还会 pull）
     * 4. 如果本地无仓库，执行 clone
     *
     * @param appCode     应用编码，用于确定工作目录名
     * @param gitUrl      Git 仓库地址
     * @param gitAcct     Git 账号（可为空）
     * @param gitPwd      Git 密码（可为空）
     * @param branchOrTag 分支或 Tag 名称
     * @param logConsumer 日志回调函数，参数为 (级别, 消息)
     * @return 工作目录的绝对路径
     * @throws Exception 如果 Git 操作失败
     */
    public String cloneOrPull(String appCode, String gitUrl, String gitAcct, String gitPwd, 
                              String branchOrTag, BiConsumer<String, String> logConsumer) throws Exception {
        
        // 验证branchOrTag，防止命令注入
        if (!isValidBranchOrTag(branchOrTag)) {
            throw new IllegalArgumentException("无效的分支/Tag名称: " + branchOrTag);
        }
        
        // 构建带认证的Git URL
        String authUrl = buildAuthUrl(gitUrl, gitAcct, gitPwd);
        
        // 工作目录：workspace/appCode
        File workDir = new File(directoryConfig.getWorkspaceDir(), appCode);
        
        if (workDir.exists() && new File(workDir, ".git").exists()) {
            // 已存在仓库，执行fetch和checkout
            logConsumer.accept("INFO", "检测到已有仓库，执行更新操作...");
            executeGitCommand(workDir, new String[]{"git", "fetch", "--all", "--tags"}, logConsumer);
            executeGitCommand(workDir, new String[]{"git", "checkout", branchOrTag}, logConsumer);
            
            // 判断是分支还是Tag，只有分支才执行pull
            if (isBranch(workDir, branchOrTag, logConsumer)) {
                logConsumer.accept("INFO", "检测到是分支，执行pull操作...");
                executeGitCommand(workDir, new String[]{"git", "pull", "origin", branchOrTag}, logConsumer);
            } else {
                logConsumer.accept("INFO", "检测到是Tag，跳过pull操作");
            }
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
     * 验证分支/Tag名称是否合法（防止命令注入）
     */
    private boolean isValidBranchOrTag(String branchOrTag) {
        if (branchOrTag == null || branchOrTag.trim().isEmpty()) {
            return false;
        }
        // 只允许字母、数字、下划线、横线、点、斜杠
        return branchOrTag.matches("^[a-zA-Z0-9_\\-./]+$");
    }
    
    /**
     * 判断是否为分支（而非Tag）
     */
    private boolean isBranch(File workDir, String branchOrTag, BiConsumer<String, String> logConsumer) {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "branch", "-r", "--list", "origin/" + branchOrTag);
            pb.directory(workDir);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                process.waitFor();
                return line != null && !line.trim().isEmpty();
            }
        } catch (Exception e) {
            logConsumer.accept("WARN", "判断分支/Tag类型失败: " + e.getMessage());
            return false;
        }
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
