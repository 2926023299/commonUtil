package com.tool.otsutil.service.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.tool.otsutil.model.dto.ApiDto.*;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * API管理器，统一管理和调用所有API
 * 支持动态注册API配置，提供通用的API调用方法
 */
@Slf4j
@Component
public class ApiManager {

    private final HttpService httpService;
    private final Map<String, ApiConfig> apiConfigs = new HashMap<>();

    public ApiManager(HttpService httpService) {
        this.httpService = httpService;
        // 初始化时注册所有API配置
        registerDefaultApis();
    }

    /**
     * 注册默认API配置
     */
    private void registerDefaultApis() {
        //全自动馈线投入率
        registerApi("autoFeederRates",
                "http://25.86.162.57/devInfo-gateway/iesweb-rtuinfo/autoFeederCoverageRate/getAutoFeederInvestedRates?areaId=0&monthDate={1}",
                new TypeReference<ApiResponse<AutoFeederRate>>() {});

        //终端在线率
        registerApi("onlineRates",
                "http://25.86.162.57/gateway/iesweb-rate-calculate/onlineRate/getRtuOnlineWeekRate?parentId=0&date={1}&rtuType={2}",
                new TypeReference<ApiResponse<OnlineRate>>() {});

        //指标统计率 {配电自动化覆盖率，联络开关可控率}
        registerApi("indicatorStatisticRates",
                "http://25.86.162.57/gateway/iesweb-rate-calculate/indicatorStatistic/getIndicatorStatisticList?areaId=0&time={1}&rateType={2}",
                new TypeReference<ApiResponse<IndicatorStatisticRate>>() {});

        // 智能站房覆盖率
        registerApi("intelDistributeCoverage",
                "http://25.86.162.57/devInfo-gateway/iesweb-rtuinfo/intelDistributeCoverageRate/getIntelDistributeCoverageStatistic?areaId=0&time={1}",
                new TypeReference<ApiResponse<IntelDistributeCoverage>>() {});

        // 终端频繁掉线率
        registerApi("rtuRreqUnline",
                "http://25.86.162.57/devInfo-gateway/iesweb-rtuinfo/rtuRreqUnline/rate?areaId=0&type=0&date={1}&startTime={2}&endTime={3}&unlineNumber=100",
                new TypeReference<ApiResponse<RtuRreqUnline>>() {});

        // 自动化缺陷消除归档率
        registerApi("defectArchivingRate",
                "http://25.86.162.57/devInfo-gateway/iesweb-rtuinfo/defectArchiving/getDefectArchivingData?areaId=0&startTime={1}&endTime={2}",
                new TypeReference<ApiResponse<DefectArchivingRate>>() {});

        // 获取终端遥控成功率
        registerApi("remoteSuccessRate",
                "http://25.86.162.57/gateway/iesweb-rate-calculate/indicatorStatistic/getRemoteSuccessRateFromES?areaId={1}&type={2}&date={3}&startTime={4}&endTime={5}",
                new TypeReference<ApiResponse<RemoteSuccessRate>>() {});
    }

    /**
     * 注册API配置
     * @param apiKey API唯一标识
     * @param urlTemplate URL模板，支持{1}, {2}等占位符
     * @param responseType 响应类型引用
     */
    public void registerApi(String apiKey, String urlTemplate, TypeReference<? extends ApiResponse<?>> responseType) {
        apiConfigs.put(apiKey, new ApiConfig(urlTemplate, responseType));
        log.info("API registered: {}", apiKey);
    }

    /**
     * 注册API配置
     * @param apiKey API唯一标识
     * @param apiConfig API配置对象
     */
    public void registerApi(String apiKey, ApiConfig apiConfig) {
        apiConfigs.put(apiKey, apiConfig);
        log.info("API registered: {}", apiKey);
    }

    /**
     * 移除API配置
     * @param apiKey API唯一标识
     */
    public void unregisterApi(String apiKey) {
        apiConfigs.remove(apiKey);
        log.info("API unregistered: {}", apiKey);
    }

    /**
     * 通用API调用方法
     * @param apiKey API唯一标识
     * @param params 调用参数，按顺序替换URL模板中的占位符
     * @param <T> 响应数据类型
     * @return API响应结果
     */
    @SuppressWarnings("unchecked")
    public <T> ApiResponse<T> callApi(String apiKey, String... params) {
        ApiConfig config = apiConfigs.get(apiKey);
        if (config == null) {
            log.error("API not found: {}", apiKey);
            return createErrorResponse("API not found: " + apiKey);
        }

        // 构建完整URL
        String url = buildUrl(config.getUrlTemplate(), params);
        log.info("Calling API: {} with URL: {}", apiKey, url);

        ApiResponse<T> response = null;
        int retryCount = 0;
        int maxRetries = 1;

        while (retryCount <= maxRetries) {
            try {
                // 调用HTTP服务获取数据，使用精确的类型转换
                TypeReference<ApiResponse<T>> typeReference = (TypeReference<ApiResponse<T>>) config.getResponseType();
                response = httpService.fetchData(url, typeReference);
                
                // 检查响应是否有效
                if (isValidResponse(response)) {
                    log.info("API call successful: {}", apiKey);
                    return response;
                } else {
                    log.warn("API returned invalid response: {}", apiKey);
                }
            } catch (Exception e) {
                log.error("API call failed (attempt {}): {}", retryCount + 1, apiKey, e);
            }

            retryCount++;
            
            // 如果还有重试次数，等待后重试
            if (retryCount <= maxRetries) {
                log.info("Retrying API call: {} (attempt {}/{})", apiKey, retryCount + 1, maxRetries + 1);
                try {
                    Thread.sleep(1000); // 等待1秒后重试
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("Retry interrupted for API: {}", apiKey, ie);
                    break;
                }
            }
        }

        log.error("All API call attempts failed: {}", apiKey);
        return createErrorResponse("API call failed after " + (maxRetries + 1) + " attempts");
    }

    /**
     * 构建完整URL，替换占位符
     * @param urlTemplate URL模板
     * @param params 替换参数
     * @return 完整URL
     */
    private String buildUrl(String urlTemplate, String... params) {
        String url = urlTemplate;
        for (int i = 0; i < params.length; i++) {
            url = url.replace("{" + (i + 1) + "}", params[i]);
        }
        return url;
    }

    /**
     * 检查响应是否有效
     * @param response API响应
     * @param <T> 响应数据类型
     * @return 是否有效
     */
    private <T> boolean isValidResponse(ApiResponse<T> response) {
        if (response == null) {
            return false;
        }
        
        // 检查响应是否成功
        if (!response.isSuccess()) {
            log.warn("API returned unsuccessful response: {}", response.getMessage());
            return false;
        }
        
        // 检查数据是否为空
        if (response.getData() == null || response.getData().isEmpty()) {
            log.warn("API returned empty data");
            return false;
        }
        
        return true;
    }

    /**
     * 创建错误响应
     * @param message 错误信息
     * @param <T> 响应数据类型
     * @return 错误响应对象
     */
    private <T> ApiResponse<T> createErrorResponse(String message) {
        ApiResponse<T> errorResponse = new ApiResponse<>();
        errorResponse.setSuccess(false);
        errorResponse.setMessage(message);
        errorResponse.setData(null);
        return errorResponse;
    }

    /**
     * 获取所有已注册的API配置
     * @return API配置映射
     */
    public Map<String, ApiConfig> getAllApiConfigs() {
        return new HashMap<>(apiConfigs);
    }

    /**
     * 检查API是否已注册
     * @param apiKey API唯一标识
     * @return 是否已注册
     */
    public boolean isApiRegistered(String apiKey) {
        return apiConfigs.containsKey(apiKey);
    }
}