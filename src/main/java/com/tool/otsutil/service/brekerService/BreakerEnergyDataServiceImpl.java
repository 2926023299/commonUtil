package com.tool.otsutil.service.brekerService;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tool.otsutil.mapper.BreakerEnergyDataMapper;
import com.tool.otsutil.model.entity.BreakerEnergyData;

import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 断路器能耗数据Service实现类
 */
@Service
public class BreakerEnergyDataServiceImpl extends ServiceImpl<BreakerEnergyDataMapper, BreakerEnergyData> implements BreakerEnergyDataService {
    
    @Override
    public List<BreakerEnergyData> queryData(String breakerId, Integer dataType, Integer cityCode, Integer limit) {
        QueryWrapper<BreakerEnergyData> queryWrapper = new QueryWrapper<>();
        
        if (breakerId != null && !breakerId.isEmpty()) {
            queryWrapper.eq("breaker_id", breakerId);
        }
        
        if (dataType != null) {
            queryWrapper.eq("data_type", dataType);
        }
        
        if (cityCode != null) {
            queryWrapper.eq("city_code", cityCode);
        }
        
        queryWrapper.orderByDesc("data_time");
        queryWrapper.last("LIMIT " + limit);
        
        return list(queryWrapper);
    }
    
    @Override
    public List<BreakerEnergyData> queryDataByDateRange(Date startDate, Date endDate) {
        QueryWrapper<BreakerEnergyData> queryWrapper = new QueryWrapper<>();
        
        if (startDate != null) {
            queryWrapper.ge("file_time", startDate);
        }
        
        if (endDate != null) {
            queryWrapper.le("file_time", endDate);
        }
        
        queryWrapper.orderByAsc("file_time", "breaker_id", "data_type");
        
        return list(queryWrapper);
    }
}