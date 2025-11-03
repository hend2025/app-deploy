package com.aeye.app.deploy.controller;

import com.aeye.app.deploy.model.VerInfo;
import com.aeye.app.deploy.service.VerMgtService;
import com.aeye.app.deploy.service.BuildTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.File;

@Controller
@RequestMapping("/verBuild")
public class VerBuildController {

    @Autowired
    private VerMgtService verMgtService;

    @Autowired
    private BuildTaskService buildTaskService;

    @GetMapping("/search")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> searchVersions(@RequestParam(required = false) String appName) {
        try {
            List<VerInfo> versions;
            if (appName != null && !appName.trim().isEmpty()) {
                versions = verMgtService.searchVersionsByAppName(appName);
            } else {
                versions = verMgtService.getAllVersions();
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", versions);
            response.put("total", versions.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "搜索版本失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/build")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> startBuild(@RequestBody Map<String, String> request) {
        try {
            String appCode = request.get("appCode");
            String targetVersion = request.get("targetVersion");

            if (appCode == null || appCode.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "应用ID不能为空");
                return ResponseEntity.ok(response);
            }

            if (targetVersion == null || targetVersion.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "目标版本号不能为空");
                return ResponseEntity.ok(response);
            }

            // 获取应用信息
            VerInfo appVersion = verMgtService.getVersionById(appCode);
            if (appVersion == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "应用不存在");
                return ResponseEntity.ok(response);
            }

            String scriptPath = appVersion.getScript();
            if (scriptPath == null || scriptPath.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "应用未配置脚本路径");
                return ResponseEntity.ok(response);
            }

            // 检查脚本文件是否存在
            File scriptFile = new File(scriptPath);
            if (!scriptFile.exists()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "脚本文件不存在: " + scriptPath);
                return ResponseEntity.ok(response);
            }

            // 启动构建任务
            buildTaskService.startBuild(appVersion,targetVersion);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "构建任务已启动");

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
    public ResponseEntity<Map<String, Object>> stopBuild(@RequestBody Map<String, String> request) {
        try {
            String appCode = request.get("appCode");

            if (appCode == null || appCode.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "应用ID不能为空");
                return ResponseEntity.ok(response);
            }

            // 获取应用信息
            VerInfo appVersion = verMgtService.getVersionById(appCode);
            if (appVersion == null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "应用不存在");
                return ResponseEntity.ok(response);
            }

            boolean success = buildTaskService.stopBuild(appCode);
            String message = success ? "构建任务已停止" : "停止构建任务失败";

            Map<String, Object> response = new HashMap<>();
            response.put("success", success);
            response.put("message", message);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "停止操作失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

}