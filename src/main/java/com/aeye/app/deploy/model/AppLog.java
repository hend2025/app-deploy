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

    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;

    private String appCode;

    private String version;

    private String logLevel;

    private String logContent;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date logTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * 内存序号（仅用于缓冲区增量读取，不持久化）
     */
    @TableField(exist = false)
    private Long seq;

}
