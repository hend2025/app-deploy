package com.aeye.app.deploy.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
@TableName("t_app_info")
public class AppInfo {

    /**
     * 应用编码（主键）
     * <p>
     * 唯一标识一个应用，同时也是JAR文件的名称（不含.jar后缀）
     */
    @TableId(type = IdType.INPUT)
    private String appCode;

    /**
     * 当前运行版本号
     * <p>
     * 记录应用最后一次启动时使用的版本号
     */
    private String version;

    /**
     * JVM启动参数
     * <p>
     * 支持多行配置，每行一个参数，如：-Xmx1024m、-Dspring.profiles.active=prod
     */
    private String params;

    /**
     * 最后更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

}
