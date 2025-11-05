package com.aeye.app.deploy.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.*;

@Service
public class FileReadService {

    @Value("${app.directory.logs:/home/logs}")
    private String logsDir;

    /**
     * 获取日志文件列表
     */
    public Map<String, Object> getLogFiles() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            File logsDir;
            // 判断是否为classpath路径
            if (this.logsDir.startsWith("classpath:")) {
                String classpathPath = this.logsDir.substring("classpath:".length());
                logsDir = ResourceUtils.getFile("classpath:" + classpathPath);
            } else {
                // 绝对路径或相对路径
                logsDir = new File(this.logsDir);
            }
            
            if (!logsDir.exists() || !logsDir.isDirectory()) {
                result.put("success", false);
                result.put("message", "日志目录不存在: " + this.logsDir);
                result.put("configuredDirectory", this.logsDir);
                return result;
            }
            
            // 根据配置的文件模式获取文件
            File[] logFiles = logsDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".log"));
            
            if (logFiles == null || logFiles.length == 0) {
                result.put("success", true);
                result.put("logFiles", new String[0]);
                result.put("count", 0);
                result.put("message", "未找到匹配的日志文件");
                result.put("configuredDirectory", this.logsDir);
                return result;
            }
            
            // 构建文件信息列表并进行分组过滤
            java.util.List<Map<String, Object>> allFileList = new java.util.ArrayList<>();
            for (File file : logFiles) {
                Map<String, Object> fileInfo = new HashMap<>();
                fileInfo.put("fileName", file.getName());
                fileInfo.put("displayName", file.getName());
                fileInfo.put("fullPath", file.getAbsolutePath());
                fileInfo.put("size", file.length());
                fileInfo.put("lastModified", file.lastModified());
                allFileList.add(fileInfo);
            }
            
            // 按应用和版本分组，每个版本最多保留最新的3条记录
            java.util.List<Map<String, Object>> filteredFileList = filterFilesByAppAndVersion(allFileList, 3);
            
            result.put("success", true);
            result.put("logFiles", filteredFileList);
            result.put("count", filteredFileList.size());
            result.put("configuredDirectory", this.logsDir);
            result.put("message", "成功获取 " + filteredFileList.size() + " 个日志文件（已过滤）");
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "获取日志文件列表失败: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
            result.put("configuredDirectory", logsDir);
        }
        
        return result;
    }
    
    /**
     * 按应用和版本分组过滤文件，每个版本最多保留最新的N条记录
     */
    private java.util.List<Map<String, Object>> filterFilesByAppAndVersion(
            java.util.List<Map<String, Object>> allFiles, int maxPerVersion) {
        
        // 按应用和版本分组
        java.util.Map<String, java.util.List<Map<String, Object>>> versionGroups = new java.util.HashMap<>();
        
        for (Map<String, Object> fileInfo : allFiles) {
            String fileName = (String) fileInfo.get("fileName");
            
            // 解析文件名，提取应用名和版本号
            AppVersionInfo versionInfo = extractAppAndVersion(fileName);
            String groupKey = versionInfo.appName + "-" + versionInfo.version;
            
            if (!versionGroups.containsKey(groupKey)) {
                versionGroups.put(groupKey, new java.util.ArrayList<>());
            }
            
            versionGroups.get(groupKey).add(fileInfo);
        }
        
        // 对每个版本组内的文件按文件名降序排序，并只保留前N条
        java.util.List<Map<String, Object>> filteredFiles = new java.util.ArrayList<>();
        
        for (java.util.List<Map<String, Object>> files : versionGroups.values()) {
            // 按文件名降序排序（Z-A）
            files.sort((a, b) -> {
                String nameA = (String) a.get("fileName");
                String nameB = (String) b.get("fileName");
                return nameB.compareTo(nameA);
            });
            
            // 只保留前maxPerVersion条
            java.util.List<Map<String, Object>> topFiles = files.subList(0, 
                Math.min(maxPerVersion, files.size()));
            filteredFiles.addAll(topFiles);
        }
        
        // 最终结果按文件名降序排序
        filteredFiles.sort((a, b) -> {
            String nameA = (String) a.get("fileName");
            String nameB = (String) b.get("fileName");
            return nameB.compareTo(nameA);
        });
        
        return filteredFiles;
    }
    
    /**
     * 从文件名中提取应用名和版本号
     */
    private AppVersionInfo extractAppAndVersion(String fileName) {
        // 移除.log后缀
        String nameWithoutExt = fileName.replaceAll("\\.log$", "");
        
        // 尝试匹配格式：appCode-version_timestamp
        // 例如：medical-platform-1.0.0_20241010_123456.log
        java.util.regex.Pattern pattern1 = java.util.regex.Pattern.compile("^(.+)-(\\d+\\.\\d+\\.\\d+)_(\\d{8}_\\d{6})$");
        java.util.regex.Matcher matcher = pattern1.matcher(nameWithoutExt);
        
        if (matcher.matches()) {
            return new AppVersionInfo(matcher.group(1), matcher.group(2));
        }
        
        // 尝试匹配格式：appCode-version_timestamp（其他版本格式）
        java.util.regex.Pattern pattern2 = java.util.regex.Pattern.compile("^(.+)-([^_]+)_(\\d{8}_\\d{6})$");
        matcher = pattern2.matcher(nameWithoutExt);
        
        if (matcher.matches()) {
            return new AppVersionInfo(matcher.group(1), matcher.group(2));
        }
        
        // 尝试匹配格式：appCode_timestamp（无版本号）
        java.util.regex.Pattern pattern3 = java.util.regex.Pattern.compile("^(.+)_(\\d{8}_\\d{6})$");
        matcher = pattern3.matcher(nameWithoutExt);
        
        if (matcher.matches()) {
            return new AppVersionInfo(matcher.group(1), "no-version");
        }
        
        // 如果都不匹配，返回整个文件名作为应用名
        return new AppVersionInfo(nameWithoutExt, "no-version");
    }
    
    /**
     * 应用版本信息内部类
     */
    private static class AppVersionInfo {
        String appName;
        String version;
        
        AppVersionInfo(String appName, String version) {
            this.appName = appName;
            this.version = version;
        }
    }
    
    /**
     * 读取指定文件（流式读取，避免大文件OOM）
     */
    public Map<String, Object> readFile(String fileName) {
        Map<String, Object> result = new HashMap<>();
        try {
            File file;
            // 判断是否为classpath路径
            if (logsDir.startsWith("classpath:")) {
                String classpathPath = logsDir.substring("classpath:".length());
                file = ResourceUtils.getFile("classpath:" + classpathPath + "/" + fileName);
            } else {
                // 判断 fileName 是否为绝对路径
                File tempFile = new File(fileName);
                if (tempFile.isAbsolute()) {
                    // 如果是绝对路径，直接使用
                    file = tempFile;
                } else {
                    // 如果是相对路径，拼接 logsDir
                    file = new File(logsDir, fileName);
                }
            }
            
            if (!file.exists()) {
                result.put("success", false);
                result.put("message", "文件不存在: " + fileName);
                result.put("configuredDirectory", logsDir);
                result.put("resolvedPath", file.getAbsolutePath());
                return result;
            }
            
            // 检查文件大小，如果超过100MB，建议使用下载
            long fileSize = file.length();
            long fileSizeMB = fileSize / (1024 * 1024);
            if (fileSizeMB > 100) {
                result.put("success", false);
                result.put("message", "文件过大 (" + fileSizeMB + " MB)，请使用下载功能");
                result.put("size", fileSize);
                result.put("configuredDirectory", logsDir);
                return result;
            }
            
            // 使用流式读取，避免大文件内存溢出
            StringBuilder content = new StringBuilder();
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE);
            
            try (ReadableByteChannel channel = Files.newByteChannel(file.toPath());
                 BufferedReader reader = new BufferedReader(Channels.newReader(channel, decoder, -1))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
            
            result.put("success", true);
            result.put("fileName", fileName);
            result.put("content", content.toString());
            result.put("size", file.length());
            result.put("lastModified", file.lastModified());
            result.put("fullPath", file.getAbsolutePath());
            result.put("configuredDirectory", logsDir);
            result.put("message", "文件读取成功");
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "读取文件失败: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
            result.put("configuredDirectory", logsDir);
        }
        
        return result;
    }
    
    /**
     * 读取指定文件最后N行
     */
    public Map<String, Object> readFileLastLines(String fileName, int lastLines) {
        Map<String, Object> result = new HashMap<>();
        try {
            File file;
            // 判断是否为classpath路径
            if (logsDir.startsWith("classpath:")) {
                String classpathPath = logsDir.substring("classpath:".length());
                file = ResourceUtils.getFile("classpath:" + classpathPath + "/" + fileName);
            } else {
                // 判断 fileName 是否为绝对路径
                File tempFile = new File(fileName);
                if (tempFile.isAbsolute()) {
                    // 如果是绝对路径，直接使用
                    file = tempFile;
                } else {
                    // 如果是相对路径，拼接 logsDir
                    file = new File(logsDir, fileName);
                }
            }
            
            if (!file.exists()) {
                result.put("success", false);
                result.put("message", "文件不存在: " + fileName);
                result.put("configuredDirectory", logsDir);
                result.put("resolvedPath", file.getAbsolutePath());
                return result;
            }
            
            // 检查文件大小，如果文件过大给出警告
            long fileSize = file.length();
            long fileSizeMB = fileSize / (1024 * 1024);
            if (fileSizeMB > 50) {
                result.put("warning", String.format("文件较大 (%d MB)，建议下载后查看", fileSizeMB));
            }
            
            // 限制最大读取行数，防止内存溢出
            int maxAllowedLines = 5000;
            if (lastLines > maxAllowedLines) {
                lastLines = maxAllowedLines;
                result.put("warning", String.format("请求行数过多，已限制为 %d 行", maxAllowedLines));
            }
            
            // 使用流式读取最后N行，避免大文件内存溢出
            List<String> lastLinesList = readLastLinesFromFile(file, lastLines);
            int totalLines = countFileLines(file);
            int actualLines = lastLinesList.size();
            int startLine = Math.max(0, totalLines - actualLines);
            
            // 构建最后N行的内容
            StringBuilder lastLinesContent = new StringBuilder();
            for (int i = 0; i < lastLinesList.size(); i++) {
                lastLinesContent.append(lastLinesList.get(i));
                if (i < lastLinesList.size() - 1) {
                    lastLinesContent.append("\n");
                }
            }
            
            result.put("success", true);
            result.put("fileName", fileName);
            result.put("content", lastLinesContent.toString());
            result.put("totalLines", totalLines);
            result.put("startLine", startLine);
            result.put("actualLines", actualLines);
            result.put("requestedLines", lastLines);
            result.put("size", file.length());
            result.put("lastModified", file.lastModified());
            result.put("fullPath", file.getAbsolutePath());
            result.put("configuredDirectory", logsDir);
            result.put("message", "文件最后 " + actualLines + " 行读取成功");
            
        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "读取文件失败: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
            result.put("configuredDirectory", logsDir);
        }
        
        return result;
    }
    
    /**
     * 增量读取文件内容（从指定行数开始读取新增内容）
     * 在读取过程中同时计算总行数，避免单独调用countFileLines方法
     */
    public Map<String, Object> readFileIncremental(String fileName, int fromLine) {
        Map<String, Object> result = new HashMap<>();

        try {
            File file;
            // 判断是否为classpath路径
            if (logsDir.startsWith("classpath:")) {
                String classpathPath = logsDir.substring("classpath:".length());
                file = ResourceUtils.getFile("classpath:" + classpathPath + "/" + fileName);
            } else {
                // 判断 fileName 是否为绝对路径
                File tempFile = new File(fileName);
                if (tempFile.isAbsolute()) {
                    // 如果是绝对路径，直接使用
                    file = tempFile;
                } else {
                    // 如果是相对路径，拼接 logsDir
                    file = new File(logsDir, fileName);
                }
            }
            
            if (!file.exists()) {
                result.put("success", false);
                result.put("message", "文件不存在: " + fileName);
                result.put("configuredDirectory", logsDir);
                result.put("resolvedPath", file.getAbsolutePath());
                return result;
            }
            
            // 限制单次增量读取的最大行数，防止内存溢出
            int maxIncrementalLines = 2000;
            
            // 使用流式读取，从指定行开始读取新增部分
            // 在读取过程中同时计算总行数，避免单独调用countFileLines方法
            List<String> newLinesList = new ArrayList<>();
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE);
            
            int currentLine = 0;
            int totalLines = 0;
            
            try (ReadableByteChannel channel = Files.newByteChannel(file.toPath());
                 BufferedReader reader = new BufferedReader(Channels.newReader(channel, decoder, -1))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    totalLines++;
                    // 如果当前行号大于等于起始行号，且新增行数未超过限制
                    if (currentLine >= fromLine && newLinesList.size() < maxIncrementalLines) {
                        newLinesList.add(line);
                    }
                    currentLine++;
                }
            }
            
            // 检测日志文件是否被轮转（文件行数减少）
            if (totalLines < fromLine && fromLine > 0) {
                result.put("success", true);
                result.put("fileName", fileName);
                result.put("content", "");
                result.put("totalLines", totalLines);
                result.put("fromLine", fromLine);
                result.put("newLines", 0);
                result.put("hasNewContent", false);
                result.put("fileRotated", true);
                result.put("size", file.length());
                result.put("lastModified", file.lastModified());
                result.put("configuredDirectory", logsDir);
                result.put("message", "检测到日志文件已轮转，请重新加载");
                return result;
            }
            
            // 如果文件行数没有增加，返回空内容
            if (totalLines <= fromLine) {
                result.put("success", true);
                result.put("fileName", fileName);
                result.put("content", "");
                result.put("totalLines", totalLines);
                result.put("fromLine", fromLine);
                result.put("newLines", 0);
                result.put("hasNewContent", false);
                result.put("fileRotated", false);
                result.put("size", file.length());
                result.put("lastModified", file.lastModified());
                result.put("configuredDirectory", logsDir);
                result.put("message", "没有新增内容");
                return result;
            }
            
            // 如果新增行数超过限制，只保留最后maxIncrementalLines行
            int newLinesCount = totalLines - fromLine;
            if (newLinesCount > maxIncrementalLines) {
                // 只保留最后maxIncrementalLines行
                newLinesList = newLinesList.subList(Math.max(0, newLinesList.size() - maxIncrementalLines), newLinesList.size());
                newLinesCount = maxIncrementalLines;
                result.put("warning", String.format("新增行数过多，只读取最后 %d 行", maxIncrementalLines));
            }
            
            // 构建新增内容
            StringBuilder newContent = new StringBuilder();
            for (int i = 0; i < newLinesList.size(); i++) {
                newContent.append(newLinesList.get(i));
                if (i < newLinesList.size() - 1) {
                    newContent.append("\n");
                }
            }
            
            result.put("success", true);
            result.put("fileName", fileName);
            result.put("content", newContent.toString());
            result.put("totalLines", totalLines);
            result.put("fromLine", fromLine);
            result.put("newLines", newLinesCount);
            result.put("hasNewContent", true);
            result.put("fileRotated", false);
            result.put("size", file.length());
            result.put("lastModified", file.lastModified());
            result.put("fullPath", file.getAbsolutePath());
            result.put("configuredDirectory", logsDir);
            result.put("message", "成功读取 " + newLinesCount + " 行新增内容");
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "读取文件失败: " + e.getMessage());
            result.put("error", e.getClass().getSimpleName());
            result.put("configuredDirectory", logsDir);
        }
        
        return result;
    }
    
    /**
     * 从文件末尾读取最后N行（流式读取，避免内存溢出）
     */
    private List<String> readLastLinesFromFile(File file, int lastLines) throws IOException {
        List<String> lines = new ArrayList<>();
    
        // 添加文件存在和可读性检查
        if (!file.exists() || !file.canRead()) {
            throw new IOException("文件不存在或无读取权限: " + file.getAbsolutePath());
        }
    
        // 使用 CharsetDecoder 并设置错误处理策略
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
    
        // 正确使用 CharsetDecoder 的方法
        try (ReadableByteChannel channel = Files.newByteChannel(file.toPath());
             BufferedReader reader = new BufferedReader(Channels.newReader(channel, decoder, -1))) {
            // 使用队列存储最后N行，避免反向读取的复杂性
            ArrayDeque<String> lineQueue = new ArrayDeque<>(lastLines);
            String line;
    
            while ((line = reader.readLine()) != null) {
                lineQueue.offer(line);
                // 保持队列大小不超过lastLines
                if (lineQueue.size() > lastLines) {
                    lineQueue.poll();
                }
            }
    
            lines.addAll(lineQueue);
        } catch (Exception e) {
            // 捕获并包装所有可能的异常，提供更详细的错误信息
            throw new IOException("读取文件最后几行时出错: " + file.getAbsolutePath(), e);
        }
    
        return lines;
    }
    
    
    /**
     * 从文件指定行范围读取内容（流式读取）
     */
    private List<String> readLinesFromFile(File file, int fromLine, int toLine) throws IOException {
        List<String> lines = new ArrayList<>();
        
        // 使用 CharsetDecoder 并设置错误处理策略
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        
        // 正确使用 CharsetDecoder 的方法
        try (ReadableByteChannel channel = Files.newByteChannel(file.toPath());
             BufferedReader reader = new BufferedReader(Channels.newReader(channel, decoder, -1))) {
            String line;
            int currentLine = 0;
            
            while ((line = reader.readLine()) != null) {
                if (currentLine >= fromLine && currentLine < toLine) {
                    lines.add(line);
                }
                if (currentLine >= toLine) {
                    break;
                }
                currentLine++;
            }
        }
        
        return lines;
    }
    
    /**
     * 统计文件总行数（流式读取，避免内存溢出）
     */
    private int countFileLines(File file) throws IOException {
        int lineCount = 0;
        
        // 使用 CharsetDecoder 并设置错误处理策略
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        
        // 正确使用 CharsetDecoder 的方法
        try (ReadableByteChannel channel = Files.newByteChannel(file.toPath());
             BufferedReader reader = new BufferedReader(Channels.newReader(channel, decoder, -1))) {
            while (reader.readLine() != null) {
                lineCount++;
            }
        }
        
        return lineCount;
    }

}