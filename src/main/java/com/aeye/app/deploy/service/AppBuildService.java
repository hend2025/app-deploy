package com.aeye.app.deploy.service;

import com.aeye.app.deploy.mapper.AppBuildMapper;
import com.aeye.app.deploy.model.AppBuild;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.Date;
import java.util.List;

/**
 * 版本管理服务
 * 提供版本构建配置的CRUD操作和状态管理
 *
 * @author aeye
 * @since 1.0.0
 */
@Service
public class AppBuildService {

    @Autowired
    private AppBuildMapper appBuildMapper;

    /**
     * 服务初始化
     * 应用启动时重置所有构建状态为就绪，防止异常中断导致的状态不一致
     */
    @PostConstruct
    public void init() {
        LambdaUpdateWrapper<AppBuild> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.set(AppBuild::getStatus, "0");
        appBuildMapper.update(null, updateWrapper);
    }

    /**
     * 获取所有版本信息
     */
    public List<AppBuild> getAllVersions() {
        return appBuildMapper.selectList(null);
    }

    /**
     * 根据ID获取版本信息
     */
    public AppBuild getVersionById(String id) {
        return appBuildMapper.selectById(id);
    }

    /**
     * 更新构建状态
     *
     * @param id     应用编码
     * @param status 状态：0-就绪，1-构建中
     * @param verNo  版本号（构建成功时更新）
     * @return 更新后的版本信息
     */
    public AppBuild updateStatus(String id, String status, String verNo) {
        AppBuild verInfo = appBuildMapper.selectById(id);
        if (verInfo == null) {
            return null;
        }

        verInfo.setStatus(status);
        verInfo.setUpdateTime(new Date());
        if (verNo != null && !verNo.trim().isEmpty()) {
            verInfo.setVersion(verNo);
        }

        appBuildMapper.updateById(verInfo);
        return verInfo;
    }

    /**
     * 更新日志文件路径
     *
     * @param appCode 应用编码
     * @param logFile 日志文件路径
     */
    public void updateLogFile(String appCode, String logFile) {
        LambdaUpdateWrapper<AppBuild> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(AppBuild::getAppCode, appCode)
                .set(AppBuild::getLogFile, logFile);
        appBuildMapper.update(null, updateWrapper);
    }

    /**
     * 根据应用名称搜索版本
     */
    public List<AppBuild> searchVersionsByAppName(String appName) {
        if (appName == null || appName.trim().isEmpty()) {
            return getAllVersions();
        }
        LambdaQueryWrapper<AppBuild> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(AppBuild::getAppName, appName);
        return appBuildMapper.selectList(wrapper);
    }

    /**
     * 保存版本信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveVersion(AppBuild verInfo) {
        verInfo.setUpdateTime(new Date());
        AppBuild existing = appBuildMapper.selectById(verInfo.getAppCode());
        if (existing != null) {
            appBuildMapper.updateById(verInfo);
        } else {
            appBuildMapper.insert(verInfo);
        }
    }

    /**
     * 删除版本信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteVersion(String appCode) {
        appBuildMapper.deleteById(appCode);
    }

}
