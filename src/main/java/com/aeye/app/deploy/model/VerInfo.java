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
     * Windows 构建脚本内容
     */
    private String scriptCmd;

    /**
     * Linux 构建脚本内容
     */
    private String scriptSh;

    private String logFile;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;
}
