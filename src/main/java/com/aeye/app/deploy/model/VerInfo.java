package com.aeye.app.deploy.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
@TableName("t_ver_info")
public class VerInfo {

    @TableId(type = IdType.INPUT)
    private String appCode;

    private String appName;

    private String version;

    private String status;

    /**
     * 应用类型：1-java, 2-vue
     */
    private String appType;

    /**
     * Git仓库地址
     */
    private String gitUrl;

    /**
     * Git账号
     */
    private String gitAcct;

    /**
     * Git密码
     */
    private String gitPwd;

    /**
     * 构建参数
     */
    private String params;

    /**
     * 构建脚本
     */
    private String buildScript;

    /**
     * 归档文件（相对路径，多个用逗号分隔）
     */
    private String archiveFiles;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
