package com.aeye.app.deploy.controller;

import com.aeye.app.deploy.config.DirectoryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 文件浏览器控制器
 * 
 * 提供 home-directory 目录的文件浏览、查看和下载功能，包括：
 * - 目录内容列表
 * - 文本文件内容预览（分块加载）
 * - 单文件下载
 * - 多文件批量下载（ZIP打包）
 * 
 * 安全特性：
 * - 路径遍历攻击防护
 * - 文件大小限制
 * - 批量操作数量限制
 */
@RestController
@RequestMapping("/fileBrowser")
public class FileBrowserController {

    private static final Logger logger = LoggerFactory.getLogger(FileBrowserController.class);

    @Autowired
    private DirectoryConfig directoryConfig;

    /** 可预览的文本文件扩展名集合 */
    private static final Set<String> TEXT_EXTENSIONS = new HashSet<>(Arrays.asList(
        "txt", "log", "sh", "bat", "cmd", "ps1", "py", "java", "js", "ts", "json", 
        "xml", "yml", "yaml", "properties", "conf", "cfg", "ini", "md", "html", 
        "css", "sql", "vue", "jsx", "tsx", "go", "rs", "c", "cpp", "h", "hpp"
    ));

    /** 目录列表最大返回文件数，防止大目录导致性能问题 */
    private static final int MAX_LIST_FILES = 1000;
    
    /** 文本预览最大文件大小（50MB），超过此大小的文件不允许预览 */
    private static final long MAX_PREVIEW_SIZE = 50 * 1024 * 1024;
    
    /** 批量下载最大文件数量 */
    private static final int MAX_BATCH_DOWNLOAD = 100;

    /**
     * 获取目录内容列表
     * 
     * 返回指定路径下的所有文件和目录信息，包括名称、大小、修改时间等。
     * 结果按目录优先、名称字母顺序排序。
     *
     * @param path 相对于 home-directory 的路径，空字符串表示根目录
     * @return 包含文件列表的响应，每个文件包含 name、isDirectory、size、sizeText、
     *         lastModified、lastModifiedTime、isTextFile 等字段
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listDirectory(@RequestParam(defaultValue = "") String path) {
        Map<String, Object> response = new HashMap<>();
        try {
            String homeDir = directoryConfig.getHomeDirectory();
            Path basePath = Paths.get(homeDir).normalize();
            Path targetPath = basePath.resolve(path).normalize();

            // 安全检查：确保目标路径在 home 目录内，防止路径遍历攻击
            if (!targetPath.startsWith(basePath)) {
                response.put("success", false);
                response.put("message", "非法路径");
                return ResponseEntity.ok(response);
            }

            if (!Files.exists(targetPath) || !Files.isDirectory(targetPath)) {
                response.put("success", false);
                response.put("message", "目录不存在");
                return ResponseEntity.ok(response);
            }

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            List<Map<String, Object>> items = new ArrayList<>();

            // 判断是否为根目录（用于过滤workspace目录）
            final boolean isRootDir = path == null || path.isEmpty();
            
            try (Stream<Path> stream = Files.list(targetPath)) {
                items = stream
                // 根目录下隐藏workspace目录
                .filter(p -> !(isRootDir && p.toFile().isDirectory() && "workspace".equals(p.toFile().getName())))
                .limit(MAX_LIST_FILES)  // 限制最大文件数，防止大目录导致性能问题
                .map(p -> {
                    Map<String, Object> item = new HashMap<>();
                    File f = p.toFile();
                    item.put("name", f.getName());
                    item.put("isDirectory", f.isDirectory());
                    item.put("size", f.isDirectory() ? 0 : f.length());
                    item.put("sizeText", f.isDirectory() ? "-" : formatFileSize(f.length()));
                    item.put("lastModified", sdf.format(new Date(f.lastModified())));
                    item.put("lastModifiedTime", f.lastModified());
                    // 只有文本文件且大小在限制内才标记为可预览
                    item.put("isTextFile", isTextFile(f.getName()) && f.length() <= MAX_PREVIEW_SIZE);
                    return item;
                })
                .sorted((a, b) -> {
                    // 排序规则：目录排在前面，同类型按名称字母顺序排序
                    boolean aDir = (Boolean) a.get("isDirectory");
                    boolean bDir = (Boolean) b.get("isDirectory");
                    if (aDir != bDir) return bDir ? 1 : -1;
                    return ((String) a.get("name")).compareToIgnoreCase((String) b.get("name"));
                })
                .collect(Collectors.toList());
            }

            response.put("success", true);
            response.put("data", items);
            response.put("currentPath", path);
            response.put("homeDirectory", homeDir);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取目录内容失败", e);
            response.put("success", false);
            response.put("message", "获取目录内容失败");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 读取文本文件内容（支持分块加载）
     * 
     * 使用 RandomAccessFile 实现分块读取，支持大文件的渐进式加载。
     * 前端可通过滚动触发加载更多内容。
     *
     * @param path   相对于 home-directory 的文件路径
     * @param offset 读取起始位置（字节偏移量）
     * @param limit  单次读取的最大字节数，默认 100KB
     * @return 包含文件内容的响应，包括 content、offset、fileSize、hasMore 等字段
     */
    @GetMapping("/content")
    public ResponseEntity<Map<String, Object>> getFileContent(
            @RequestParam String path,
            @RequestParam(defaultValue = "0") long offset,
            @RequestParam(defaultValue = "102400") int limit) {
        Map<String, Object> response = new HashMap<>();
        try {
            String homeDir = directoryConfig.getHomeDirectory();
            Path basePath = Paths.get(homeDir).normalize();
            Path filePath = basePath.resolve(path).normalize();

            // 安全检查：防止路径遍历攻击
            if (!filePath.startsWith(basePath)) {
                response.put("success", false);
                response.put("message", "非法路径");
                return ResponseEntity.ok(response);
            }

            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                response.put("success", false);
                response.put("message", "文件不存在");
                return ResponseEntity.ok(response);
            }

            File file = filePath.toFile();
            long fileSize = file.length();

            // 检查文件大小限制，防止加载过大文件导致内存溢出
            if (fileSize > MAX_PREVIEW_SIZE) {
                response.put("success", false);
                response.put("message", "文件过大，无法预览（最大支持50MB）");
                return ResponseEntity.ok(response);
            }

            byte[] bytes;
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                // 如果偏移量已超过文件大小，返回空内容
                if (offset >= fileSize) {
                    response.put("success", true);
                    response.put("content", "");
                    response.put("offset", fileSize);
                    response.put("fileSize", fileSize);
                    response.put("hasMore", false);
                    return ResponseEntity.ok(response);
                }

                // 定位到指定偏移量并读取数据
                raf.seek(offset);
                int readSize = (int) Math.min(limit, fileSize - offset);
                bytes = new byte[readSize];
                raf.readFully(bytes);
            }

            String content = new String(bytes, StandardCharsets.UTF_8);
            long newOffset = offset + bytes.length;

            response.put("success", true);
            response.put("content", content);
            response.put("offset", newOffset);
            response.put("fileSize", fileSize);
            response.put("hasMore", newOffset < fileSize);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("读取文件内容失败", e);
            response.put("success", false);
            response.put("message", "读取文件失败");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 下载单个文件
     * 
     * 支持任意类型文件的下载，文件名使用 UTF-8 编码以支持中文。
     *
     * @param path 相对于 home-directory 的文件路径
     * @return 文件资源流，浏览器将触发下载
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(@RequestParam String path) {
        try {
            String homeDir = directoryConfig.getHomeDirectory();
            Path basePath = Paths.get(homeDir).normalize();
            Path filePath = basePath.resolve(path).normalize();

            // 安全检查：防止路径遍历攻击
            if (!filePath.startsWith(basePath)) {
                return ResponseEntity.badRequest().build();
            }

            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(filePath.toFile());
            String fileName = filePath.getFileName().toString();
            // URL 编码文件名，支持中文等特殊字符
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString())
                    .replace("+", "%20");

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + encodedFileName)
                    .body(resource);
        } catch (Exception e) {
            logger.error("下载文件失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 批量下载文件（打包为ZIP）
     * 
     * 将多个文件或目录打包为 ZIP 文件下载。
     * 支持目录递归打包，临时文件在下载完成后自动清理。
     *
     * @param request 包含 paths 字段的请求体，paths 为文件路径列表
     * @return ZIP 文件资源流
     */
    @PostMapping("/downloadMultiple")
    public ResponseEntity<Resource> downloadMultiple(@RequestBody Map<String, Object> request) {
        File tempFile = null;
        try {
            String homeDir = directoryConfig.getHomeDirectory();
            Path basePath = Paths.get(homeDir).normalize();
            
            @SuppressWarnings("unchecked")
            List<String> paths = (List<String>) request.get("paths");
            if (paths == null || paths.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            // 限制批量下载文件数量，防止资源滥用
            if (paths.size() > MAX_BATCH_DOWNLOAD) {
                logger.warn("批量下载文件数量超过限制: {}", paths.size());
                return ResponseEntity.badRequest().build();
            }

            // 创建临时 ZIP 文件
            tempFile = File.createTempFile("download_", ".zip");
            final File zipFile = tempFile;
            
            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tempFile))) {
                for (String filePath : paths) {
                    Path fullPath = basePath.resolve(filePath).normalize();
                    // 安全检查：跳过非法路径
                    if (!fullPath.startsWith(basePath)) continue;
                    
                    if (Files.exists(fullPath)) {
                        if (Files.isDirectory(fullPath)) {
                            addDirectoryToZip(zos, fullPath, fullPath.getFileName().toString());
                        } else {
                            addFileToZip(zos, fullPath, fullPath.getFileName().toString());
                        }
                    }
                }
            }

            // 使用自定义 Resource，在流关闭后自动删除临时文件
            Resource resource = new FileSystemResource(tempFile) {
                @Override
                public InputStream getInputStream() throws IOException {
                    return new FileInputStream(zipFile) {
                        @Override
                        public void close() throws IOException {
                            super.close();
                            // 流关闭后删除临时文件，防止磁盘空间泄漏
                            if (zipFile.exists()) {
                                zipFile.delete();
                            }
                        }
                    };
                }
            };
            
            String zipName = "files_" + System.currentTimeMillis() + ".zip";
            tempFile = null; // 置空防止 finally 中重复删除

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename*=UTF-8''" + zipName)
                    .body(resource);
        } catch (Exception e) {
            logger.error("批量下载失败", e);
            return ResponseEntity.internalServerError().build();
        } finally {
            // 异常情况下清理临时文件
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * 添加单个文件到 ZIP 输出流
     *
     * @param zos       ZIP 输出流
     * @param file      要添加的文件路径
     * @param entryName ZIP 中的条目名称
     * @throws IOException 文件读取或写入异常
     */
    private void addFileToZip(ZipOutputStream zos, Path file, String entryName) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        Files.copy(file, zos);
        zos.closeEntry();
    }

    /**
     * 递归添加目录到 ZIP 输出流
     * 
     * 遍历目录下所有文件和子目录，保持目录结构添加到 ZIP 中。
     *
     * @param zos      ZIP 输出流
     * @param dir      要添加的目录路径
     * @param baseName ZIP 中的基础目录名
     * @throws IOException 文件读取或写入异常
     */
    private void addDirectoryToZip(ZipOutputStream zos, Path dir, String baseName) throws IOException {
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.forEach(path -> {
                try {
                    // 构建 ZIP 条目名称，统一使用正斜杠作为路径分隔符
                    String entryName = baseName + "/" + dir.relativize(path).toString().replace("\\", "/");
                    if (Files.isDirectory(path)) {
                        // 目录条目需要以斜杠结尾
                        if (!entryName.endsWith("/")) entryName += "/";
                        zos.putNextEntry(new ZipEntry(entryName));
                        zos.closeEntry();
                    } else {
                        addFileToZip(zos, path, entryName);
                    }
                } catch (IOException e) {
                    logger.error("添加文件到ZIP失败: {}", path, e);
                }
            });
        }
    }

    /**
     * 判断文件是否为可预览的文本文件
     * 
     * 根据文件扩展名判断，支持常见的文本文件格式。
     *
     * @param fileName 文件名
     * @return 如果是文本文件返回 true，否则返回 false
     */
    private boolean isTextFile(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) return false;
        String ext = fileName.substring(dotIndex + 1).toLowerCase();
        return TEXT_EXTENSIONS.contains(ext);
    }

    /**
     * 格式化文件大小为人类可读的字符串
     *
     * @param size 文件大小（字节）
     * @return 格式化后的字符串，如 "1.5 MB"
     */
    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
    }
}
