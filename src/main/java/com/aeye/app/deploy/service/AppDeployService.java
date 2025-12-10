package com.aeye.app.deploy.service;

import com.aeye.app.deploy.mapper.AppDeployMapper;
import com.aeye.app.deploy.model.AppDeploy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

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

}
