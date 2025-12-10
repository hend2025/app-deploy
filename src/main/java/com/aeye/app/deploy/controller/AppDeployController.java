package com.aeye.app.deploy.controller;

import com.aeye.app.deploy.model.AppDeploy;
import com.aeye.app.deploy.service.AppDeployService;
import com.aeye.app.deploy.service.JarProcessService;
import com.aeye.app.deploy.util.ProcessUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/appDeploy")
public class AppDeployController {

    private static final Logger logger = LoggerFactory.getLogger(AppDeployController.class);

    @Autowired
    private AppDeployService appDeployService;

    @Autowired
    private JarProcessService jarProcessService;

    /** 进程状态缓存，用于减少系统调用频率 */
    private final Map<String, CachedProcessInfo> processCache = new ConcurrentHashMap<>();
    
    /** 缓存过期时间（毫秒） */
    private static final long CACHE_EXPIRY_MS = 10000;
    
    /**
     * 进程信息缓存内部类
     * <p>
     * 缓存进程ID和时间戳，支持过期检测
     */
    private static class CachedProcessInfo {
        String pid;
        long timestamp;
        
        CachedProcessInfo(String pid, long timestamp) {
            this.pid = pid;
            this.timestamp = timestamp;
        }
        
        /**
         * 检查缓存是否已过期
         * @return true-已过期，false-未过期
         */
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS;
        }
    }

    /**
     * 获取应用列表
     * <p>
     * 返回所有应用的配置信息和实时运行状态（通过检测系统进程获取）
     *
     * @param appName 应用名称过滤条件（可选，支持模糊匹配）
     * @return 应用列表，包含状态：1-就绪，2-运行中
     */
    @GetMapping("/list")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAppList(@RequestParam(required = false) String appName) {
        try {
            List<AppDeploy> allApps = appDeployService.getAllApps();
            
            // 如果指定了应用名称，进行过滤
            if (appName != null && !appName.trim().isEmpty()) {
                allApps = allApps.stream()
                    .filter(app -> app.getAppCode().toLowerCase().contains(appName.toLowerCase()))
                    .collect(java.util.stream.Collectors.toList());
            }
            
            // 批量获取进程状态，减少系统调用
            Map<String, String> processStatusMap = batchGetProcessStatus(allApps);
            
            // 转换为前端需要的格式并检查进程状态
            List<Map<String, Object>> resultList = new ArrayList<>();
            
            for (AppDeploy appInfo : allApps) {
                Map<String, Object> appMap = new HashMap<>();
                
                // 设置应用基本信息
                appMap.put("appCode", appInfo.getAppCode());
                appMap.put("version", appInfo.getVersion());
                appMap.put("params", appInfo.getParams());
                
                // 从批量获取的结果中获取进程ID
                String pid = processStatusMap.get(appInfo.getAppCode());
                
                if (pid != null) {
                    // 进程正在运行，状态设置为2（运行）
                    appMap.put("status", "2");
                    appMap.put("pid", pid);
                } else {
                    // 进程未运行，状态设置为1（就绪）
                    appMap.put("status", "1");
                    appMap.put("pid", null);
                }
                
                // 设置更新时间
                if (appInfo.getUpdateTime() != null) {
                    appMap.put("updateTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                        .format(appInfo.getUpdateTime()));
                } else {
                    appMap.put("updateTime", null);
                }
                
                resultList.add(appMap);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", resultList);
            response.put("total", resultList.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("获取应用列表失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取应用列表失败，请稍后重试");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 启动应用
     * <p>
     * 异步启动指定版本的JAR应用，支持自定义JVM参数
     *
     * @param request 请求参数：appCode-应用编码，version-版本号，params-JVM参数
     * @return 启动结果
     */
    @PostMapping("/start")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> startApp(@RequestBody Map<String, String> request) {
        try {
            String appCode = request.get("appCode");
            String version = request.get("version");
            String params = request.get("params");

            if (appCode == null || appCode.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "应用编码不能为空");
                return ResponseEntity.ok(response);
            }
            
            // 验证appCode格式，防止路径遍历攻击
            if (!isValidAppCode(appCode)) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "应用编码格式不正确");
                return ResponseEntity.ok(response);
            }

            AppDeploy appInfo = appDeployService.getAppByCode(appCode);
            if (appInfo == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "应用编码不存在");
                return ResponseEntity.ok(response);
            }

            jarProcessService.startJarApp(appCode, version, params);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "应用启动任务已提交");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("启动应用失败: {}", request.get("appCode"), e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "启动应用失败，请稍后重试");
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * 验证appCode格式（只允许字母、数字、下划线、横线）
     */
    private boolean isValidAppCode(String appCode) {
        return appCode != null && appCode.matches("^[a-zA-Z0-9_\\-]+$");
    }

    /**
     * 保存应用配置
     * <p>
     * 新增或更新应用配置信息
     *
     * @param appInfo 应用信息
     * @return 保存结果
     */
    @PostMapping("/save")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveApp(@RequestBody AppDeploy appInfo) {
        try {
            if (appInfo.getAppCode() == null || appInfo.getAppCode().trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "应用编码不能为空");
                return ResponseEntity.ok(response);
            }

            appDeployService.saveApp(appInfo);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "保存成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("保存应用配置失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "保存失败，请稍后重试");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 删除应用配置
     *
     * @param request 请求参数：appCode-应用编码
     * @return 删除结果
     */
    @PostMapping("/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteApp(@RequestBody Map<String, String> request) {
        try {
            String appCode = request.get("appCode");
            if (appCode == null || appCode.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "应用编码不能为空");
                return ResponseEntity.ok(response);
            }

            appDeployService.deleteApp(appCode);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "删除成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("删除应用配置失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "删除失败，请稍后重试");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 停止应用
     * <p>
     * 通过进程ID强制终止应用进程
     *
     * @param request 请求参数：appCode-应用编码，pid-进程ID
     * @return 停止结果
     */
    @PostMapping("/stop")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> stopApp(@RequestBody Map<String, String> request) {
        try {
            String appCode = request.get("appCode");
            String pid = request.get("pid");

            if (appCode == null || appCode.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "应用编码不能为空");
                return ResponseEntity.ok(response);
            }

            if (pid == null || pid.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "进程ID不能为空");
                return ResponseEntity.ok(response);
            }

            // 停止进程
            boolean success = ProcessUtil.killProcess(pid);
            
            // 清除缓存，让下次查询时重新获取进程状态
            processCache.remove(appCode);

            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("message", success ? "应用已停止" : "停止应用失败");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("停止应用失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "停止应用失败，请稍后重试");
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * 批量获取进程状态，减少系统调用（只调用一次系统命令）
     */
    private Map<String, String> batchGetProcessStatus(List<AppDeploy> apps) {
        Map<String, String> result = new HashMap<>();
        
        // 检查缓存，找出需要更新的应用
        List<String> appsToCheck = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        
        for (AppDeploy app : apps) {
            String appCode = app.getAppCode();
            CachedProcessInfo cached = processCache.get(appCode);
            
            // 如果缓存存在且未过期，直接使用缓存
            if (cached != null && !cached.isExpired()) {
                if (cached.pid != null) {
                    result.put(appCode, cached.pid);
                }
            } else {
                // 需要检查的应用
                appsToCheck.add(appCode);
            }
        }
        
        // 如果有需要检查的应用，批量获取进程信息
        if (!appsToCheck.isEmpty()) {
            // 一次性获取所有Java进程信息
            Map<String, String> allProcesses = ProcessUtil.getAllJarProcessIds();
            
            // 更新缓存和结果
            for (String appCode : appsToCheck) {
                String pid = allProcesses.get(appCode);
                
                if (pid != null) {
                    result.put(appCode, pid);
                    processCache.put(appCode, new CachedProcessInfo(pid, currentTime));
                } else {
                    // 进程不存在，更新缓存为null（而不是移除，这样可以避免频繁查询）
                    processCache.put(appCode, new CachedProcessInfo(null, currentTime));
                }
            }
        }
        
        return result;
    }
    
}