package com.aeye.app.deploy.service;

import com.aeye.app.deploy.mapper.AppInfoMapper;
import com.aeye.app.deploy.model.AppInfo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 应用管理服务
 * <p>
 * 提供应用配置信息的CRUD操作，数据持久化到数据库。
 *
 * @author aeye
 * @since 1.0.0
 */
@Service
public class AppMgtService {

    @Autowired
    private AppInfoMapper appInfoMapper;

    /**
     * 获取所有应用
     */
    public List<AppInfo> getAllApps() {
        return appInfoMapper.selectList(null);
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
    @Transactional(rollbackFor = Exception.class)
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
    @Transactional(rollbackFor = Exception.class)
    public void deleteApp(String appCode) {
        appInfoMapper.deleteById(appCode);
    }

}
