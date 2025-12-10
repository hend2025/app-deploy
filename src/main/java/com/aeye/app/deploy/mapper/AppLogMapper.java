package com.aeye.app.deploy.mapper;

import com.aeye.app.deploy.model.AppLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface AppLogMapper extends BaseMapper<AppLog> {

}
