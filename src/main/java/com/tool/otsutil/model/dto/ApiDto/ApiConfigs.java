package com.tool.otsutil.model.dto.ApiDto;

import com.fasterxml.jackson.core.type.TypeReference;

import java.util.HashMap;
import java.util.Map;

public class ApiConfigs {
    private static final Map<String, ApiConfig> configs = new HashMap<>();

    static {

        //全自动馈线投入率
        configs.put("autoFeederRates", new ApiConfig(
                "http://25.86.162.57/devInfo-gateway/iesweb-rtuinfo/autoFeederCoverageRate/getAutoFeederInvestedRates?areaId=0&monthDate={1}",
                new TypeReference<ApiResponse<AutoFeederRate>>() {}
        ));

        //终端在线率
        configs.put("onlineRates", new ApiConfig(
                "http://25.86.162.57/gateway/iesweb-rate-calculate/onlineRate/getRtuOnlineWeekRate?parentId=0&date={1}&rtuType={2}",
                new TypeReference<ApiResponse<OnlineRate>>() {}
        ));

        //指标统计率 {配电自动化覆盖率，联络开关可控率}
        configs.put("indicatorStatisticRates", new ApiConfig(
                "http://25.86.162.57/gateway/iesweb-rate-calculate/indicatorStatistic/getIndicatorStatisticList?areaId=0&time={1}&rateType={2}",
                new TypeReference<ApiResponse<IndicatorStatisticRate>>() {}
        ));

        // 智能站房覆盖率
        configs.put("intelDistributeCoverage", new ApiConfig(
                "http://25.86.162.57/devInfo-gateway/iesweb-rtuinfo/intelDistributeCoverageRate/getIntelDistributeCoverageStatistic?areaId=0&time={1}",
                new TypeReference<ApiResponse<IntelDistributeCoverage>>() {}
        ));

        // 终端频繁掉线率
        configs.put("rtuRreqUnline", new ApiConfig(
                "http://25.86.162.57/devInfo-gateway/iesweb-rtuinfo/rtuRreqUnline/rate?areaId=0&type=0&date={1}&startTime={2}&endTime={3}&unlineNumber=100",
                new TypeReference<ApiResponse<RtuRreqUnline>>() {}
        ));

        // 自动化缺陷消除归档率
        configs.put("defectArchivingRate", new ApiConfig(
                "http://25.86.162.57/devInfo-gateway/iesweb-rtuinfo/defectArchiving/getDefectArchivingData?areaId=0&startTime={1}&endTime={2}",
                new TypeReference<ApiResponse<DefectArchivingRate>>() {}
        ));

        // 获取终端遥控成功率
        configs.put("remoteSuccessRate", new ApiConfig(
                "http://25.86.162.57/gateway/iesweb-rate-calculate/indicatorStatistic/getRemoteSuccessRateFromES?areaId={1}&type={2}&date={3}&startTime={4}&endTime={5}",
                new TypeReference<ApiResponse<RemoteSuccessRate>>() {}
        ));

        // 可以继续添加其他接口配置
    }

    public static ApiConfig getConfig(String key) {
        return configs.get(key);
    }
}