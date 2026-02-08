package com.tool.otsutil.util;

import com.tool.otsutil.model.dto.ApiDto.*;

import java.math.BigDecimal;

/**
 * 比率数据提取器接口，用于从不同类型的数据对象中提取areaId和rate
 */
public interface RateExtractor<T> {
    
    /**
     * 从数据对象中提取区域ID
     * @param data 数据对象
     * @return 区域ID
     */
    String getAreaId(T data);
    
    /**
     * 从数据对象中提取比率值
     * @param data 数据对象
     * @return 比率值
     */
    BigDecimal getRate(T data);
    
    /**
     * 获取提取器支持的数据类型
     * @return 支持的数据类型
     */
    Class<T> getSupportedType();
    
    /**
     * 默认实现类，用于处理AutoFeederRate类型
     */
    class AutoFeederRateExtractor implements RateExtractor<AutoFeederRate> {
        @Override
        public String getAreaId(AutoFeederRate data) {
            return data.getAreaId();
        }
        
        @Override
        public BigDecimal getRate(AutoFeederRate data) {
            return data.getRate();
        }
        
        @Override
        public Class<AutoFeederRate> getSupportedType() {
            return AutoFeederRate.class;
        }
    }
    
    /**
     * 默认实现类，用于处理OnlineRate类型
     */
    class OnlineRateExtractor implements RateExtractor<OnlineRate> {
        @Override
        public String getAreaId(OnlineRate data) {
            return data.getAreaId();
        }
        
        @Override
        public BigDecimal getRate(OnlineRate data) {
            return data.getRate();
        }
        
        @Override
        public Class<OnlineRate> getSupportedType() {
            return OnlineRate.class;
        }
    }
    
    /**
     * 默认实现类，用于处理IndicatorStatisticRate类型
     */
    class IndicatorStatisticRateExtractor implements RateExtractor<IndicatorStatisticRate> {
        private final String rateType;
        
        public IndicatorStatisticRateExtractor(String rateType) {
            this.rateType = rateType;
        }
        
        @Override
        public String getAreaId(IndicatorStatisticRate data) {
            return data.getAreaId();
        }
        
        @Override
        public BigDecimal getRate(IndicatorStatisticRate data) {
            switch (rateType) {
                case "faCoverageRate":
                    return data.getFaCoverageRate();
                case "contactEquipmentControllableRate":
                    return data.getContactEquipmentControllableRate();
                case "remoteSuccessRate":
                    return data.getRemoteSuccessRate();
                case "rtuOnlineRate":
                    return data.getRtuOnlineRate();
                default:
                    // 默认返回配电自动化覆盖率
                    return data.getFaCoverageRate();
            }
        }
        
        @Override
        public Class<IndicatorStatisticRate> getSupportedType() {
            return IndicatorStatisticRate.class;
        }
    }
    
    /**
     * 默认实现类，用于处理DefectArchivingRate类型
     */
    class DefectArchivingRateExtractor implements RateExtractor<DefectArchivingRate> {
        @Override
        public String getAreaId(DefectArchivingRate data) {
            return data.getAreaId();
        }
        
        @Override
        public BigDecimal getRate(DefectArchivingRate data) {
            return data.getRate();
        }
        
        @Override
        public Class<DefectArchivingRate> getSupportedType() {
            return DefectArchivingRate.class;
        }
    }
    
    /**
     * 默认实现类，用于处理RemoteSuccessRate类型
     */
    class RemoteSuccessRateExtractor implements RateExtractor<RemoteSuccessRate> {
        @Override
        public String getAreaId(RemoteSuccessRate data) {
            return data.getAreaId();
        }
        
        @Override
        public BigDecimal getRate(RemoteSuccessRate data) {
            return data.getRate();
        }
        
        @Override
        public Class<RemoteSuccessRate> getSupportedType() {
            return RemoteSuccessRate.class;
        }
    }
    
    /**
     * 默认实现类，用于处理RtuRreqUnline类型
     */
    class RtuRreqUnlineExtractor implements RateExtractor<RtuRreqUnline> {
        @Override
        public String getAreaId(RtuRreqUnline data) {
            return data.getAreaId();
        }
        
        @Override
        public BigDecimal getRate(RtuRreqUnline data) {
            return data.getRate();
        }
        
        @Override
        public Class<RtuRreqUnline> getSupportedType() {
            return RtuRreqUnline.class;
        }
    }
    
    /**
     * 默认实现类，用于处理IntelDistributeCoverage类型
     */
    class IntelDistributeCoverageExtractor implements RateExtractor<IntelDistributeCoverage> {
        @Override
        public String getAreaId(IntelDistributeCoverage data) {
            return data.getAreaId();
        }
        
        @Override
        public BigDecimal getRate(IntelDistributeCoverage data) {
            return data.getRate();
        }
        
        @Override
        public Class<IntelDistributeCoverage> getSupportedType() {
            return IntelDistributeCoverage.class;
        }
    }
}
