package com.aeye.app.deploy.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
@TableName("t_app_log")
public class AppLog {

    /**
     * 日志ID（自增主键）
     */
    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;

    /**
     * 应用编码
     * <p>
     * 关联到具体的应用
     */
    private String appCode;

    /**
     * 版本号/分支名
     * <p>
     * 记录产生日志时的版本信息
     */
    private String version;

    /**
     * 日志级别
     * <p>
     * 支持：DEBUG, INFO, WARN, ERROR
     */
    private String logLevel;

    /**
     * 日志内容
     */
    private String logContent;

    /**
     * 日志产生时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date logTime;

    /**
     * 内存序号
     * <p>
     * 仅用于内存缓冲区的增量读取，不持久化到数据库。
     * 客户端通过此序号实现日志的增量拉取。
     */
    @TableField(exist = false)
    private Long seq;

}
