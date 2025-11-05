package com.aeye.app.deploy.controller;

import com.aeye.app.deploy.model.AppInfo;
import com.aeye.app.deploy.service.AppMgtService;
import com.aeye.app.deploy.service.JarProcessService;
import com.aeye.app.deploy.util.ProcessUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/appMgt")
public class AppMgtController {

    @Autowired
    private AppMgtService appMgtService;

    @Autowired
    private JarProcessService jarProcessService;

    // 进程状态缓存，减少系统调用
    private final Map<String, CachedProcessInfo> processCache = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MS = 10000; // 5秒缓存
    
    // 缓存进程信息的内部类
    private static class CachedProcessInfo {
        String pid;
        long timestamp;
        
        CachedProcessInfo(String pid, long timestamp) {
            this.pid = pid;
            this.timestamp = timestamp;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS;
        }
    }

    @GetMapping("/list")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAppList(@RequestParam(required = false) String appName) {
        try {
            // 直接从apps.json获取应用列表
            List<AppInfo> allApps = appMgtService.getAllApps();
            
            // 如果指定了应用名称，进行过滤
            if (appName != null && !appName.trim().isEmpty()) {
                allApps = allApps.stream()
                    .filter(app -> app.getAppCode().toLowerCase().contains(appName.toLowerCase()))
                    .collect(java.util.stream.Collectors.toList());
            }
            
            // 转换为前端需要的格式并检查进程状态
            List<Map<String, Object>> resultList = new ArrayList<>();
            
            for (AppInfo appInfo : allApps) {
                Map<String, Object> appMap = new HashMap<>();
                
                // 设置应用基本信息
                appMap.put("appCode", appInfo.getAppCode());
                appMap.put("version", appInfo.getVersion());
                appMap.put("params", appInfo.getParams());
                appMap.put("logFile", appInfo.getLogFile());
                
                // 使用缓存检查Java进程状态
                String pid = getCachedProcessId(appInfo.getAppCode());
                
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
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取应用列表失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

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

            AppInfo appInfo = appMgtService.getAppByCode(appCode);
            if (appInfo == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "应用编码不存在");
                return ResponseEntity.ok(response);
            }

            jarProcessService.startJarApp(appCode,version,params);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "应用启动任务已提交");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

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
            Thread.sleep(1000);
            
            // 清除缓存
            if (success) {
                processCache.remove(appCode);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("message", success ? "应用已停止" : "停止应用失败");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "停止应用失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
    
    /**
     * 获取缓存的进程ID（带缓存机制）
     */
    private String getCachedProcessId(String appCode) {
        CachedProcessInfo cached = processCache.get(appCode);
        
        // 如果缓存存在且未过期，直接返回
        if (cached != null && !cached.isExpired()) {
            return cached.pid;
        }
        
        // 缓存不存在或已过期，重新获取
        String pid = ProcessUtil.getJarProcessId(appCode);
        
        // 更新缓存
        if (pid != null) {
            processCache.put(appCode, new CachedProcessInfo(pid, System.currentTimeMillis()));
        } else {
            // 如果进程不存在，从缓存中移除
            processCache.remove(appCode);
        }
        
        return pid;
    }

}