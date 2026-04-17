package com.tool.otsutil.mapper;

import com.tool.otsutil.model.dto.TopoDeviceRecord;
import com.tool.otsutil.model.dto.TopoFeederRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TopoDeviceMapper {
    /**
     * 查询设备信息，关联feeder_b表获取馈线名称
     */
    List<TopoDeviceRecord> selectDeviceInfoByIds(@Param("ids") List<String> ids);

    /**
     * 查询馈线名称
     */
    List<TopoFeederRecord> selectFeederInfoByIds(@Param("ids") List<String> ids);
}
