package com.aeye.app.deploy.service;

import com.aeye.app.deploy.mapper.AppInfoMapper;
import com.aeye.app.deploy.model.AppInfo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.Date;
import java.util.List;

@Service
public class AppMgtService {

    @Value("${app.directory.data:/home/data}")
    private String dataDir;

    @Autowired
    private AppInfoMapper appInfoMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        // 如果数据库为空，从JSON文件导入初始数据
        if (appInfoMapper.selectCount(null) == 0) {
            importFromJson();
        }
    }

    /**
     * 从JSON文件导入数据到数据库
     */
    private void importFromJson() {
        try {
            File externalFile = new File(dataDir, "apps.json");
            List<AppInfo> apps;
            
            if (externalFile.exists()) {
                apps = objectMapper.readValue(externalFile, new TypeReference<List<AppInfo>>() {});
            } else {
                ClassPathResource resource = new ClassPathResource("data/apps.json");
                if (resource.exists()) {
                    apps = objectMapper.readValue(resource.getInputStream(), new TypeReference<List<AppInfo>>() {});
                } else {
                    return;
                }
            }
            
            for (AppInfo app : apps) {
                appInfoMapper.insert(app);
            }
            System.out.println("已从JSON导入 " + apps.size() + " 条应用数据到数据库");
        } catch (Exception e) {
            System.err.println("导入应用数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有应用
     */
    public List<AppInfo> getAllApps() {
        return appInfoMapper.selectList(null);
    }

    /**
     * 根据appCode搜索应用
     */
    public List<AppInfo> searchApps(String appCode) {
        if (appCode == null || appCode.trim().isEmpty()) {
            return getAllApps();
        }
        LambdaQueryWrapper<AppInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(AppInfo::getAppCode, appCode);
        return appInfoMapper.selectList(wrapper);
    }

    /**
     * 根据appCode获取应用
     */
    public AppInfo getAppByCode(String appCode) {
        return appInfoMapper.selectById(appCode);
    }

    /**
     * 添加或更新应用
     */
    public void saveApp(AppInfo appInfo) {
        appInfo.setUpdateTime(new Date());
        AppInfo existing = appInfoMapper.selectById(appInfo.getAppCode());
        if (existing != null) {
            appInfoMapper.updateById(appInfo);
        } else {
            appInfoMapper.insert(appInfo);
        }
    }

    /**
     * 删除应用
     */
    public void deleteApp(String appCode) {
        appInfoMapper.deleteById(appCode);
    }
}
