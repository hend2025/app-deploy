package com.aeye.app.deploy.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.util.Date;

@Data
public class AppInfo {

    private String appCode;
    private String version;
    private String params;
    private String logFile;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

}
