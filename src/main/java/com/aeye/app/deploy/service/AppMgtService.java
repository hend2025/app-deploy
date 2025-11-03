package com.aeye.app.deploy.service;

import com.aeye.app.deploy.model.AppInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AppMgtService {

    @Value("${app.directory.data:/home/data}")
    private String dataDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, AppInfo> appCache = new ConcurrentHashMap<>();

    private static final String APPS_FILE = "apps.json";

    @PostConstruct
    public void init() {
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        loadApps();
    }

    /**
     * 加载应用列表
     */
    private void loadApps() {
        try {
            File file = new File(dataDir, APPS_FILE);
            if (file.exists()) {
                AppInfo[] apps = objectMapper.readValue(file, AppInfo[].class);
                for (AppInfo app : apps) {
                    appCache.put(app.getAppCode(), app);
                }
            }
        } catch (IOException e) {
            System.err.println("加载应用列表失败: " + e.getMessage());
        }
    }

    /**
     * 保存应用列表到文件
     */
    private void saveApps() {
        try {
            File dir = new File(dataDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            File file = new File(dataDir, APPS_FILE);
            List<AppInfo> appList = new ArrayList<>(appCache.values());
            objectMapper.writeValue(file, appList);
        } catch (IOException e) {
            System.err.println("保存应用列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取所有应用
     */
    public List<AppInfo> getAllApps() {
        return new ArrayList<>(appCache.values());
    }

    /**
     * 根据appCode获取应用
     */
    public AppInfo getAppByCode(String appCode) {
        return appCache.get(appCode);
    }

    /**
     * 添加或更新应用
     */
    public void saveApp(AppInfo appInfo) {
        appInfo.setUpdateTime(new Date());
        appCache.put(appInfo.getAppCode(), appInfo);
        saveApps();
    }
}
