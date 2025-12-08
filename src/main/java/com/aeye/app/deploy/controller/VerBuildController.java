package com.aeye.app.deploy.controller;

import com.aeye.app.deploy.model.VerInfo;
import com.aeye.app.deploy.service.VerMgtService;
import com.aeye.app.deploy.service.BuildTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/verBuild")
public class VerBuildController {

    @Autowired
    private VerMgtService verMgtService;

    @Autowired
    private BuildTaskService buildTaskService;

    @GetMapping("/search")
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

            // 检查是否配置了构建脚本（根据操作系统检查对应字段）
            boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
            String scriptContent = isWindows ? appVersion.getScriptCmd() : appVersion.getScriptSh();
            if (scriptContent == null || scriptContent.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                String osType = isWindows ? "Windows(script_cmd)" : "Linux(script_sh)";
                response.put("message", "应用未配置" + osType + "构建脚本");
                return ResponseEntity.ok(response);
            }

            // 启动构建任务，获取日志文件路径
            String logFilePath = buildTaskService.startBuild(appVersion, targetVersion);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "构建任务已启动");
            response.put("logFile", logFilePath);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/save")
    public ResponseEntity<Map<String, Object>> saveVersion(@RequestBody VerInfo verInfo) {
        try {
            if (verInfo.getAppCode() == null || verInfo.getAppCode().trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "应用编码不能为空");
                return ResponseEntity.ok(response);
            }

            verMgtService.saveVersion(verInfo);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "保存成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "保存失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/delete")
    public ResponseEntity<Map<String, Object>> deleteVersion(@RequestBody Map<String, String> request) {
        try {
            String appCode = request.get("appCode");
            if (appCode == null || appCode.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "应用编码不能为空");
                return ResponseEntity.ok(response);
            }

            verMgtService.deleteVersion(appCode);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "删除成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "删除失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/stop")
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