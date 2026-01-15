package com.aeye.app.deploy.service;

import com.aeye.app.deploy.model.AppLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 应用日志服务
 */
@Service
public class AppLogService {

    private static final Logger logger = LoggerFactory.getLogger(AppLogService.class);

    @Autowired
    private AppBuildService appBuildService;

    @Autowired
    private LogBufferService logBufferService;

    /**
     * 增量读取缓冲区日志（只返回 afterSeq 之后的新日志）
     */
    public Map<String, Object> getLogsIncremental(String appCode, long afterSeq, int limit) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<AppLog> logs = logBufferService.getLogsIncremental(appCode, afterSeq, limit);

            // 如果内存无日志且是首次查询（afterSeq=0），尝试读取配置的日志文件
            if (logs.isEmpty() && afterSeq == 0) {
                com.aeye.app.deploy.model.AppBuild appBuild = appBuildService.getVersionById(appCode);
                if (appBuild != null && appBuild.getLogFile() != null && !appBuild.getLogFile().isEmpty()) {
                    List<String> fileLines = readLastNLines(new java.io.File(appBuild.getLogFile()),
                            limit > 0 ? limit : 1000);
                    long seq = 1;
                    for (String line : fileLines) {
                        AppLog log = new AppLog();
                        log.setAppCode(appCode);
                        log.setLogContent(line);
                        log.setSeq(seq++);
                        // 尝试解析时间？暂不解析，保持简单
                        log.setVersion(appBuild.getVersion());
                        logs.add(log);
                    }
                }
            }

            long currentSeq = 0;
            if (!logs.isEmpty()) {
                currentSeq = logs.stream().mapToLong(AppLog::getSeq).max().orElse(currentSeq);
            }
            result.put("success", true);
            result.put("logs", logs);
            result.put("count", logs.size());
            result.put("currentSeq", currentSeq);
            result.put("message", "查询成功");
        } catch (Exception e) {
            logger.error("增量读取日志失败", e);
            result.put("success", false);
            result.put("message", "读取日志失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 读取文件最后N行
     */
    private List<String> readLastNLines(java.io.File file, int numLines) {
        List<String> lines = new ArrayList<>();
        if (!file.exists() || !file.isFile()) {
            return lines;
        }

        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
            long length = raf.length();
            if (length == 0) {
                return lines;
            }

            long position = length - 1;
            int linesFound = 0;
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();

            // 从后向前扫描换行符
            while (position >= 0) {
                raf.seek(position);
                int b = raf.read();

                if (b == '\n') {
                    // 遇到换行符，如果buffer不为空（即行不为空或者这是一个空行），则说明找到了一行
                    // 注意：由于是倒序读，遇到\n说明这一行结束（或者是上一行的开始）
                    // 但我们需要把buffer里的内容反转并变成字符串
                    // 简单起见，我们收集所有字节，直到遇到\n，然后反转收集

                    // 实际上，更简单的方法是：
                    // 倒序读取字节，遇到\n计数+1。
                    // 当计数达到numLines时，记录当前position+1为start。
                    // 然后从start开始顺序读取到文件末尾。
                    linesFound++;
                    if (linesFound > numLines) {
                        break;
                    }
                }
                position--;
            }

            // position停在需要读取的起始位置的前一个字节（或者-1）
            raf.seek(position + 1);

            // 使用BufferedReader顺序读取剩余部分
            // 注意：RandomAccessFile不是InputStream，需要转换或者直接用raf读取
            // 为了处理编码（UTF-8），使用InputStreamReader包装FileInputStream会更方便，
            // 但FileInputStream不支持seek到任意位置（虽然可以skip，但也许慢）
            // 这里既然已经定了位置，可以直接用 BufferedReader 包装 Channels.newInputStream

            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(java.nio.channels.Channels.newInputStream(raf.getChannel()),
                            java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }

            // 如果读取的行数多于numLines（因为最后通常没有换行符也算一行，或者上面的逻辑稍微多读了一点），截取最后numLines
            // 上面的逻辑：position是倒数第numLines+1个换行符的位置。
            // 从position+1开始读，应该正好读到numLines行（如果文件以换行符结尾，可能会多读一行空行？readLine会处理掉）

            // 简单的校验，保留最后numLines
            while (lines.size() > numLines) {
                lines.remove(0);
            }

        } catch (Exception e) {
            logger.error("读取日志文件失败: " + file.getAbsolutePath(), e);
        }
        return lines;
    }
}
