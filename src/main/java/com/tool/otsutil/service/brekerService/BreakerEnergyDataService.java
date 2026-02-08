package com.tool.otsutil.service.brekerService;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tool.otsutil.model.entity.BreakerEnergyData;

import java.util.Date;
import java.util.List;

/**
 * 断路器能耗数据Service
 */
public interface BreakerEnergyDataService extends IService<BreakerEnergyData> {
    
    /**
     * 查询断路器能耗数据
     * @param breakerId 断路器ID（可选）
     * @param dataType 数据类型（可选）
     * @param cityCode 城市编码（可选）
     * @param limit 返回记录数限制
     * @return 断路器能耗数据列表
     */
    List<BreakerEnergyData> queryData(String breakerId, Integer dataType, Integer cityCode, Integer limit);
    
    /**
     * 根据日期范围查询数据
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 断路器能耗数据列表
     */
    List<BreakerEnergyData> queryDataByDateRange(Date startDate, Date endDate);
}