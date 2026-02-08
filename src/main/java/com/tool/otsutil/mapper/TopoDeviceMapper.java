package com.tool.otsutil.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface TopoDeviceMapper {
    /**
     * 查询设备信息，关联feeder_b表获取馈线名称
     */
    List<Map<String, Object>> selectDeviceInfoByIds(@Param("ids") List<String> ids);

    
}