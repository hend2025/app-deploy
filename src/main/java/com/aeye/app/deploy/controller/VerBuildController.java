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

/**
 * 版本构建控制器
 * <p>
 * 提供应用版本构建管理接口，包括：
 * <ul>
 *   <li>版本配置的增删改查</li>
 *   <li>构建任务的启动/停止</li>
 *   <li>支持Git代码拉取、脚本执行、产物归档</li>
 * </ul>
 *
 * @author aeye
 * @since 1.0.0
 */
@RestController
@RequestMapping("/verBuild")
public class VerBuildController {

    @Autowired
    private VerMgtService verMgtService;

    @Autowired
    private BuildTaskService buildTaskService;

    /**
     * 搜索版本配置列表
     *
     * @param appName 应用名称过滤条件（可选，支持模糊匹配）
     * @return 版本配置列表
     */
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

    /**
     * 启动构建任务
     * <p>
     * 异步执行构建流程：Git拉取 -> 执行构建脚本 -> 归档产物
     *
     * @param request 请求参数：appCode-应用编码，branchOrTag-分支或Tag名称
     * @return 启动结果
     */
    @PostMapping("/build")
    public ResponseEntity<Map<String, Object>> startBuild(@RequestBody Map<String, String> request) {
        try {
            String appCode = request.get("appCode");
            String branchOrTag = request.get("branchOrTag");

            if (appCode == null || appCode.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "应用ID不能为空");
                return ResponseEntity.ok(response);
            }

            if (branchOrTag == null || branchOrTag.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "分支/Tag不能为空");
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

            // 检查是否配置了构建脚本
            String scriptContent = appVersion.getBuildScript();
            if (scriptContent == null || scriptContent.trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "应用未配置构建脚本");
                return ResponseEntity.ok(response);
            }

            // 启动构建任务
            buildTaskService.startBuild(appVersion, branchOrTag);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "构建任务已启动");
            response.put("appCode", appCode);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 保存版本配置
     * <p>
     * 新增或更新版本构建配置信息
     *
     * @param verInfo 版本配置信息
     * @return 保存结果
     */
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

    /**
     * 删除版本配置
     *
     * @param request 请求参数：appCode-应用编码
     * @return 删除结果
     */
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

    /**
     * 停止构建任务
     * <p>
     * 终止正在执行的构建进程
     *
     * @param request 请求参数：appCode-应用编码
     * @return 停止结果
     */
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