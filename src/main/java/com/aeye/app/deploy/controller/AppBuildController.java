package com.aeye.app.deploy.controller;

import com.aeye.app.deploy.model.AppBuild;
import com.aeye.app.deploy.service.AppBuildService;
import com.aeye.app.deploy.service.BuildTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 应用构建控制器
 * 
 * 提供应用构建配置的管理和构建任务的控制，包括：
 * - 版本配置的增删改查
 * - 启动/停止构建任务
 * - 构建参数验证
 *
 * @author aeye
 * @since 1.0.0
 */
@RestController
@RequestMapping("/appBuild")
public class AppBuildController {

    private static final Logger logger = LoggerFactory.getLogger(AppBuildController.class);

    @Autowired
    private AppBuildService appBuildService;

    @Autowired
    private BuildTaskService buildTaskService;
    
    /**
     * 验证appCode格式（只允许字母、数字、下划线、横线）
     */
    private boolean isValidAppCode(String appCode) {
        return appCode != null && appCode.matches("^[a-zA-Z0-9_\\-]+$");
    }
    
    /**
     * 验证分支/Tag名称格式
     */
    private boolean isValidBranchOrTag(String branchOrTag) {
        return branchOrTag != null && branchOrTag.matches("^[a-zA-Z0-9_\\-./]+$");
    }

    /**
     * 搜索版本列表
     * 支持按应用名称模糊搜索，返回结果中Git密码会被掩码处理
     *
     * @param appName 应用名称（可选，支持模糊匹配）
     * @return 版本列表
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchVersions(@RequestParam(required = false) String appName) {
        try {
            List<AppBuild> versions;
            if (appName != null && !appName.trim().isEmpty()) {
                versions = appBuildService.searchVersionsByAppName(appName);
            } else {
                versions = appBuildService.getAllVersions();
            }
            
            // 隐藏敏感信息（Git密码）
            for (AppBuild ver : versions) {
                if (ver.getGitPwd() != null && !ver.getGitPwd().isEmpty()) {
                    ver.setGitPwd("******");
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", versions);
            response.put("total", versions.size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("搜索版本失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "搜索版本失败，请稍后重试");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 保存版本配置
     * 新增或更新版本配置信息，如果密码为掩码则保留原密码
     *
     * @param verInfo 版本信息
     * @return 保存结果
     */
    @PostMapping("/save")
    public ResponseEntity<Map<String, Object>> saveVersion(@RequestBody AppBuild verInfo) {
        try {
            if (verInfo.getAppCode() == null || verInfo.getAppCode().trim().isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "应用编码不能为空");
                return ResponseEntity.ok(response);
            }
            
            // 验证appCode格式
            if (!isValidAppCode(verInfo.getAppCode())) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "应用编码格式不正确，只允许字母、数字、下划线和横线");
                return ResponseEntity.ok(response);
            }
            
            // 如果密码是掩码，则保留原密码
            if ("******".equals(verInfo.getGitPwd())) {
                AppBuild existing = appBuildService.getVersionById(verInfo.getAppCode());
                if (existing != null) {
                    verInfo.setGitPwd(existing.getGitPwd());
                }
            }

            appBuildService.saveVersion(verInfo);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "保存成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("保存版本配置失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "保存失败，请稍后重试");
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

            appBuildService.deleteVersion(appCode);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "删除成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("删除版本配置失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "删除失败，请稍后重试");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 启动构建任务
     * 异步执行构建，立即返回任务启动状态
     *
     * @param request 请求参数：appCode-应用编码，branchOrTag-分支或Tag名
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
            
            // 验证分支/Tag格式，防止命令注入
            if (!isValidBranchOrTag(branchOrTag)) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "分支/Tag格式不正确，只允许字母、数字、下划线、横线、点和斜杠");
                return ResponseEntity.ok(response);
            }

            // 获取应用信息
            AppBuild appVersion = appBuildService.getVersionById(appCode);
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

        } catch (IllegalStateException e) {
            // 业务异常（如构建任务已在运行）
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("启动构建任务失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "启动构建任务失败，请稍后重试");
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 停止构建任务
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
            AppBuild appVersion = appBuildService.getVersionById(appCode);
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
            logger.error("停止构建任务失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "停止操作失败，请稍后重试");
            return ResponseEntity.internalServerError().body(response);
        }
    }

}