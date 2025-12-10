package com.aeye.app.deploy.service;

import com.aeye.app.deploy.mapper.VerInfoMapper;
import com.aeye.app.deploy.model.VerInfo;
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
 * <p>
 * 提供版本构建配置的CRUD操作和状态管理。
 *
 * @author aeye
 * @since 1.0.0
 */
@Service
public class VerMgtService {

    @Autowired
    private VerInfoMapper verInfoMapper;

    /**
     * 服务初始化
     * <p>
     * 应用启动时重置所有构建状态为就绪，防止异常中断导致的状态不一致
     */
    @PostConstruct
    public void init() {
        LambdaUpdateWrapper<VerInfo> updateWrapper = new LambdaUpdateWrapper<>();
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
     * 更新构建状态
     *
     * @param id     应用编码
     * @param status 状态：0-就绪，1-构建中
     * @param verNo  版本号（构建成功时更新）
     * @return 更新后的版本信息
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
    @Transactional(rollbackFor = Exception.class)
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
    @Transactional(rollbackFor = Exception.class)
    public void deleteVersion(String appCode) {
        verInfoMapper.deleteById(appCode);
    }

}
