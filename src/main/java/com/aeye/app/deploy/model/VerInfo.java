package com.aeye.app.deploy.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 版本构建信息实体类
 * <p>
 * 用于管理应用的版本构建配置，包括Git仓库信息、构建脚本和归档配置等。
 * 对应数据库表：t_ver_info
 *
 * @author aeye
 * @since 1.0.0
 */
@Data
@TableName("t_ver_info")
public class VerInfo {

    /**
     * 应用编码（主键）
     * <p>
     * 唯一标识一个应用的构建配置
     */
    @TableId(type = IdType.INPUT)
    private String appCode;

    /**
     * 应用名称
     * <p>
     * 用于显示的友好名称
     */
    private String appName;

    /**
     * 最新构建版本号
     * <p>
     * 记录最后一次成功构建的分支或Tag名称
     */
    private String version;

    /**
     * 构建状态
     * <p>
     * 0-就绪（可构建）, 1-构建中
     */
    private String status;

    /**
     * 应用类型
     * <p>
     * 1-Java应用, 2-Vue前端应用
     */
    private String appType;

    /**
     * Git仓库地址
     * <p>
     * 支持HTTP/HTTPS协议的Git仓库URL
     */
    private String gitUrl;

    /**
     * Git账号
     * <p>
     * 用于仓库认证的用户名
     */
    private String gitAcct;

    /**
     * Git密码
     * <p>
     * 用于仓库认证的密码或Token
     */
    private String gitPwd;

    /**
     * 构建参数（保留字段）
     */
    private String params;

    /**
     * 构建脚本内容
     * <p>
     * 支持Shell/Batch脚本，可使用变量：$1（分支/Tag名称）
     */
    private String buildScript;

    /**
     * 归档文件配置
     * <p>
     * 构建产物的相对路径，支持glob模式，多个用逗号分隔。
     * 示例：target/*.jar, dist/**
     */
    private String archiveFiles;

    /**
     * 最后更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

}
