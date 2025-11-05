package com.aeye.app.deploy.service;

import com.aeye.app.deploy.model.VerInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;

@Service
public class VerMgtService {

    @Value("${app.directory.data:/home/data}")
    private String dataDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, VerInfo> versionCache = new ConcurrentHashMap<>();

    
    @PostConstruct
    public void init() {
        loadDataFromFile();
    }

    /**
     * 获取所有版本信息
     */
    public List<VerInfo> getAllVersions() {
        return new ArrayList<>(versionCache.values());
    }
    
    /**
     * 根据ID获取版本信息
     */
    public VerInfo getVersionById(String id) {
        return versionCache.get(id);
    }

    public VerInfo updateStatus(String id,String status,String verNo) {
        VerInfo appVersion = versionCache.get(id);
        if (appVersion == null) {
            return null;
        }

        appVersion.setStatus(status);
        appVersion.setUpdateTime(new Date());
        if (verNo != null && !verNo.trim().isEmpty()) {
            appVersion.setVersion(verNo);
        }

        versionCache.put(id, appVersion);
        saveDataToFile();

        return appVersion;
    }

    /**
     * 根据应用名称搜索版本
     */
    public List<VerInfo> searchVersionsByAppName(String appName) {
        return versionCache.values().stream()
                .filter(version -> version.getAppName().toLowerCase().contains(appName.toLowerCase()))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    /**
     * 从文件加载数据
     */
    private void loadDataFromFile() {
        try {
            File dataFile = new File(dataDir, "versions.json");
            if (dataFile.exists()) {
                List<VerInfo> versions = objectMapper.readValue(dataFile, new TypeReference<List<VerInfo>>() {});
                for (VerInfo version : versions) {
                    version.setStatus("0");
                    versionCache.put(version.getAppCode(), version);
                }
            } else {
                Resource resource = new ClassPathResource("data/versions.json");
                if (resource.exists()) {
                    List<VerInfo> versions = objectMapper.readValue(resource.getInputStream(), new TypeReference<List<VerInfo>>() {});
                    for (VerInfo version : versions) {
                        version.setStatus("0");
                        versionCache.put(version.getAppCode(), version);
                    }
                    saveDataToFile();
                }
            }

        }catch (Exception e){
            System.err.println("读取版本数据失败: " + e.getMessage());
        }
    }
    
    /**
     * 保存数据到文件
     */
    private void saveDataToFile() {
        try {
            File dataFile = new File(dataDir, "versions.json");
            File parentDir = dataFile.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            List<VerInfo> versions = new ArrayList<>(versionCache.values());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(dataFile, versions);
        } catch (IOException e) {
            System.err.println("保存版本数据失败: " + e.getMessage());
        }
    }

}
