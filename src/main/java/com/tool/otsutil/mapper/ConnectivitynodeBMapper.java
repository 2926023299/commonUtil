package com.tool.otsutil.mapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tool.otsutil.model.entity.ConnectivitynodeB;

@Mapper
public interface ConnectivitynodeBMapper extends BaseMapper<ConnectivitynodeB> {
    /**
     * 批量查询设备的substation_id
     * 
     * @param equipmentIDList 设备ID列表
     * @param deviceType      设备类型
     * @return 设备ID到substation_id的映射
     */
    @MapKey("equipment_ID")
    Map<BigDecimal, Map<String, Object>> selectBatchForSubstationIdsByequipmentID(
            @Param("equipmentIDList") List<BigDecimal> equipmentIDList, @Param("deviceType") long deviceType,
            @Param("companyID") BigDecimal companyID);

}
