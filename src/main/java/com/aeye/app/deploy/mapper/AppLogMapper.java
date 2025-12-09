package com.aeye.app.deploy.mapper;

import com.aeye.app.deploy.model.AppLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface AppLogMapper extends BaseMapper<AppLog> {

    /**
     * 批量插入日志
     */
    int batchInsert(@Param("list") List<AppLog> logs);

    /**
     * 查询指定应用的日志
     */
    List<AppLog> selectByAppCode(@Param("appCode") String appCode,
                                  @Param("startTime") Date startTime,
                                  @Param("endTime") Date endTime,
                                  @Param("limit") int limit);

    /**
     * 删除指定日期之前的日志
     */
    int deleteBeforeDate(@Param("beforeDate") Date beforeDate);
}
