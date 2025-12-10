package com.aeye.app.deploy.controller;

import com.aeye.app.deploy.model.VerInfo;
import com.aeye.app.deploy.service.VerMgtService;
import com.aeye.app.deploy.service.BuildTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/verBuild")
public class VerBuildController {

    private static final Logger logger = LoggerFactory.getLogger(VerBuildController.class);

    @Autowired
    private VerMgtService verMgtService;

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

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchVersions(@RequestParam(required = false) String appName) {
        try {
            List<VerInfo> versions;
            if (appName != null && !appName.trim().isEmpty()) {
                versions = verMgtService.searchVersionsByAppName(appName);
            } else {
                versions = verMgtService.getAllVersions();
            }
            
            // 隐藏敏感信息（Git密码）
            for (VerInfo ver : versions) {
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

    @PostMapping("/save")
    public ResponseEntity<Map<String, Object>> saveVersion(@RequestBody VerInfo verInfo) {
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
                VerInfo existing = verMgtService.getVersionById(verInfo.getAppCode());
                if (existing != null) {
                    verInfo.setGitPwd(existing.getGitPwd());
                }
            }

            verMgtService.saveVersion(verInfo);

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
            logger.error("删除版本配置失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "删除失败，请稍后重试");
            return ResponseEntity.internalServerError().body(response);
        }
    }

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
            logger.error("停止构建任务失败", e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "停止操作失败，请稍后重试");
            return ResponseEntity.internalServerError().body(response);
        }
    }

}