package com.aeye.app.deploy.service;

import com.aeye.app.deploy.mapper.AppDeployMapper;
import com.aeye.app.deploy.model.AppDeploy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 应用部署服务
 * 提供应用部署配置的CRUD操作
 *
 * @author aeye
 * @since 1.0.0
 */
@Service
public class AppDeployService {

    @Autowired
    private AppDeployMapper appDeployMapper;

    /**
     * 获取所有应用
     */
    public List<AppDeploy> getAllApps() {
        return appDeployMapper.selectList(null);
    }

    /**
     * 根据svcCode获取应用
     */
    public AppDeploy getAppByCode(String svcCode) {
        return appDeployMapper.selectById(svcCode);
    }

    /**
     * 添加或更新应用
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveApp(AppDeploy appInfo) {
        appInfo.setUpdateTime(new Date());
        AppDeploy existing = appDeployMapper.selectById(appInfo.getSvcCode());
        if (existing != null) {
            appDeployMapper.updateById(appInfo);
        } else {
            appDeployMapper.insert(appInfo);
        }
    }

    /**
     * 删除应用
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteApp(String svcCode) {
        appDeployMapper.deleteById(svcCode);
    }

    /**
     * 更新日志文件路径
     *
     * @param svcCode 微服务编码
     * @param logFile 日志文件绝对路径
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateLogFile(String svcCode, String logFile) {
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<AppDeploy> wrapper = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        wrapper.eq(AppDeploy::getSvcCode, svcCode)
                .set(AppDeploy::getLogFile, logFile);
        appDeployMapper.update(null, wrapper);
    }

}
