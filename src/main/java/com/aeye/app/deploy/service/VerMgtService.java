package com.aeye.app.deploy.service;

import com.aeye.app.deploy.mapper.VerInfoMapper;
import com.aeye.app.deploy.model.VerInfo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.List;

@Service
public class VerMgtService {

    @Autowired
    private VerInfoMapper verInfoMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        // 如果数据库为空，从JSON文件导入初始数据
        if (verInfoMapper.selectCount(null) == 0) {
            importFromJson();
        }
        // 启动时重置所有状态为就绪
        resetAllStatus();
    }

    /**
     * 从JSON文件导入数据到数据库
     */
    private void importFromJson() {
        try {
            ClassPathResource resource = new ClassPathResource("data/versions.json");
            if (!resource.exists()) {
                return;
            }
            List<VerInfo> versions = objectMapper.readValue(resource.getInputStream(), new TypeReference<List<VerInfo>>() {});
            
            for (VerInfo ver : versions) {
                ver.setStatus("0");
                verInfoMapper.insert(ver);
            }
            System.out.println("已从JSON导入 " + versions.size() + " 条版本数据到数据库");
        } catch (Exception e) {
            System.err.println("导入版本数据失败: " + e.getMessage());
        }
    }

    /**
     * 重置所有状态为就绪（使用批量更新提高性能）
     */
    private void resetAllStatus() {
        // 使用 LambdaUpdateWrapper 批量更新，避免逐条更新
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<VerInfo> updateWrapper = 
            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        updateWrapper.set(VerInfo::getStatus, "0");
        verInfoMapper.update(null, updateWrapper);
    }

    /**
     * 获取所有版本信息
     */
    public List<VerInfo> getAllVersions() {
        return verInfoMapper.selectList(null);
    }

    /**
     * 根据ID获取版本信息
     */
    public VerInfo getVersionById(String id) {
        return verInfoMapper.selectById(id);
    }

    /**
     * 更新状态
     */
    public VerInfo updateStatus(String id, String status, String verNo) {
        VerInfo verInfo = verInfoMapper.selectById(id);
        if (verInfo == null) {
            return null;
        }

        verInfo.setStatus(status);
        verInfo.setUpdateTime(new Date());
        if (verNo != null && !verNo.trim().isEmpty()) {
            verInfo.setVersion(verNo);
        }

        verInfoMapper.updateById(verInfo);
        return verInfo;
    }

    /**
     * 更新状态和日志文件路径
     */
    public VerInfo updateStatusAndLogFile(String id, String status, String verNo, String logFile) {
        VerInfo verInfo = verInfoMapper.selectById(id);
        if (verInfo == null) {
            return null;
        }

        verInfo.setStatus(status);
        verInfo.setUpdateTime(new Date());
        if (verNo != null && !verNo.trim().isEmpty()) {
            verInfo.setVersion(verNo);
        }
        if (logFile != null && !logFile.trim().isEmpty()) {
            verInfo.setLogFile(logFile);
        }

        verInfoMapper.updateById(verInfo);
        return verInfo;
    }

    /**
     * 根据应用名称搜索版本
     */
    public List<VerInfo> searchVersionsByAppName(String appName) {
        if (appName == null || appName.trim().isEmpty()) {
            return getAllVersions();
        }
        LambdaQueryWrapper<VerInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(VerInfo::getAppName, appName);
        return verInfoMapper.selectList(wrapper);
    }

    /**
     * 保存版本信息
     */
    public void saveVersion(VerInfo verInfo) {
        verInfo.setUpdateTime(new Date());
        VerInfo existing = verInfoMapper.selectById(verInfo.getAppCode());
        if (existing != null) {
            verInfoMapper.updateById(verInfo);
        } else {
            verInfoMapper.insert(verInfo);
        }
    }

    /**
     * 删除版本信息
     */
    public void deleteVersion(String appCode) {
        verInfoMapper.deleteById(appCode);
    }
}
