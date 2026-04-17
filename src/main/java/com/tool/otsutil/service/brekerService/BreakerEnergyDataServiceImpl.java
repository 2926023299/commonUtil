package com.tool.otsutil.service.brekerService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tool.otsutil.mapper.BreakerEnergyDataMapper;
import com.tool.otsutil.model.entity.BreakerEnergyData;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

/**
 * 断路器能耗数据Service实现类
 */
@Service
public class BreakerEnergyDataServiceImpl extends ServiceImpl<BreakerEnergyDataMapper, BreakerEnergyData> implements BreakerEnergyDataService {

    @Override
    public List<BreakerEnergyData> queryData(String breakerId, Integer dataType, Integer cityCode, Integer limit) {
        LambdaQueryWrapper<BreakerEnergyData> queryWrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(breakerId)) {
            queryWrapper.eq(BreakerEnergyData::getBreakerId, breakerId);
        }

        if (dataType != null) {
            queryWrapper.eq(BreakerEnergyData::getDataType, dataType);
        }

        if (cityCode != null) {
            queryWrapper.eq(BreakerEnergyData::getCityCode, cityCode);
        }

        queryWrapper.orderByDesc(BreakerEnergyData::getDataTime);
        if (limit != null && limit > 0) {
            queryWrapper.last("LIMIT " + limit);
        }

        return list(queryWrapper);
    }

    @Override
    public List<BreakerEnergyData> queryDataByDateRange(Date startDate, Date endDate) {
        LambdaQueryWrapper<BreakerEnergyData> queryWrapper = new LambdaQueryWrapper<>();

        if (startDate != null) {
            queryWrapper.ge(BreakerEnergyData::getFileTime, startDate);
        }

        if (endDate != null) {
            queryWrapper.le(BreakerEnergyData::getFileTime, endDate);
        }

        queryWrapper.orderByAsc(BreakerEnergyData::getFileTime)
                .orderByAsc(BreakerEnergyData::getBreakerId)
                .orderByAsc(BreakerEnergyData::getDataType);

        return list(queryWrapper);
    }
}
