package com.tool.otsutil.service;

import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tool.otsutil.exception.CustomException;
import com.tool.otsutil.model.common.AppHttpCodeEnum;
import com.tool.otsutil.model.dto.ApiDto.*;
import com.tool.otsutil.util.ExcelExportUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
public class ApiService {
    @Autowired
    private HttpService httpService;

    @Autowired
    private AreaAlisa areaAlisa;

    @Autowired
    private ExcelExportUtil excelExportUtil;

    /**
     * 获取全自动馈线投入率
     *
     * @param monthDate 月份
     * @return ApiResponse对象
     */
    public ApiResponse<AutoFeederRate> getAutoFeederRates(String monthDate) {
        return getData("autoFeederRates", monthDate);
    }

    // 获取终端在线率
    public ApiResponse<OnlineRate> getOnlineRates(String... params) {
        return getData("onlineRates", params);
    }

    // 获取指标统计,通过params指定rateType类型
    public ApiResponse<IndicatorStatisticRate> getIndicatorStatisticRates(String... params) {
        return getData("indicatorStatisticRates", params);
    }

    // 获取智能站房覆盖率
    public ApiResponse<IntelDistributeCoverage> getIntelDistributeCoverage(String monthDate) {
        return getData("intelDistributeCoverage", monthDate);
    }

    //获取终端频繁掉线率
    public ApiResponse<RtuRreqUnline> getRtuRreqUnline(String... params) {
        return getData("rtuRreqUnline", params);
    }

    // 获取自动化缺陷消除归档率
    public ApiResponse<DefectArchivingRate> getDefectArchivingRates(String... params) {
        return getData("defectArchivingRate", params);
    }

    // 获取终端遥控成功率
    public ApiResponse<RemoteSuccessRate> getRemoteSuccessRates(String... params) {
        return getData("remoteSuccessRate", params);
    }

    //------------------------------------------------------------------------------------------

    // 通过apiKey获取url并请求数据
    private <T> ApiResponse<T> getData(String apiKey, String... params) {
        ApiConfig config = ApiConfigs.getConfig(apiKey);
        if (config == null) {
            throw new IllegalArgumentException("Invalid API key: " + apiKey);
        }

        // 获取 URL 模板
        String urlTemplate = config.getUrlTemplate();
        log.info("参数：{}", params);

        // 替换 URL 模板中的占位符
        for (int i = 0; i < params.length; i++) {
            urlTemplate = urlTemplate.replace("{" + (i + 1) + "}", params[i]);
        }

        // 通过apikey获取对应的typeReference
        TypeReference<ApiResponse<T>> typeReference = (TypeReference<ApiResponse<T>>) config.getResponseType();

        return fetchDataAndParse(urlTemplate, typeReference);
    }

    // 通用方法去请求接口数据获取并解析
    private <T> ApiResponse<T> fetchDataAndParse(String url, TypeReference<ApiResponse<T>> typeReference) {
        ApiResponse<T> response = null;
        try {
            log.info("请求接口{}", url);
            // 调用http请求获取数据
            response = httpService.fetchData(url, typeReference);

            // 本地伪造mock数据
//            if (typeReference.getType().equals(new TypeReference<ApiResponse<AutoFeederRate>>() {
//            }.getType())) {
//
//                response = (ApiResponse<T>) getAutoFeederMockData();
//            } else if (typeReference.getType().equals(new TypeReference<ApiResponse<OnlineRate>>() {
//            }.getType())) {
//                response = (ApiResponse<T>) getOnlineMockData();
//            }

        } catch (Exception e) {
            log.error("请求接口{}失败", url, e);
            return createErrorResponse();
        }

        if (response == null || ObjectUtil.hasNull(response)) {
            return createErrorResponse();
        }

        LinkedList<RateResult> rateResults = parseRateData(response);
        log.info("接口数据解析:{}", rateResults);

        return response;
    }

    // 创建错误响应
    private <T> ApiResponse<T> createErrorResponse() {
        ApiResponse<T> errorResponse = new ApiResponse<>();
        errorResponse.setSuccess(false);
        errorResponse.setMessage("接口请求失败");
        errorResponse.setData(new LinkedList<>());
        return errorResponse;
    }

    // 通用解析方法
    private <T> LinkedList<RateResult> parseRateData(ApiResponse<T> response) {
        LinkedList<T> dataList = response.getData();
        if (dataList == null) {
            return new LinkedList<>();
        }

        LinkedList<RateResult> rateResultList = new LinkedList<>();

        areaAlisa.getArea().keySet().forEach(key -> {
            for (T item : dataList) {
                if (item == null) {
                    continue;
                }

                try {
                    String areaId = getAreaId(item);
                    BigDecimal rate = getRate(item);
                    String areaName = areaAlisa.getArea().get(areaId);
                    if (!ObjectUtil.equal(areaId, key)) {
                        continue;
                    }

                    if (areaId != null && rate != null) {
                        RateResult rateResult = new RateResult(areaId, areaName, rate); // 使用areaId作为areaName
                        rateResultList.add(rateResult);
                    }
                } catch (Exception e) {
                    // 记录日志或处理异常
                    System.err.println("错误解析数据: " + e.getMessage());
                }
            }
        });

        return rateResultList;
    }

    // 获取区域ID的方法
    private <T> String getAreaId(T item) {
        if (item instanceof AutoFeederRate) {
            return ((AutoFeederRate) item).getAreaId();
        } else if (item instanceof OnlineRate) {
            return ((OnlineRate) item).getAreaId();
        } else if (item instanceof IndicatorStatisticRate) {
            return ((IndicatorStatisticRate) item).getAreaId();
        } else if (item instanceof IntelDistributeCoverage) {
            return ((IntelDistributeCoverage) item).getAreaId();
        } else if (item instanceof RtuRreqUnline) {
            return ((RtuRreqUnline) item).getAreaId();
        } else if (item instanceof DefectArchivingRate) {
            return ((DefectArchivingRate) item).getAreaId();
        } else if (item instanceof RemoteSuccessRate) {
            return ((RemoteSuccessRate) item).getAreaId();
        }

        return null;
    }

    // 获取返回的参数中率的方法
    private <T> BigDecimal getRate(T item) {
        if (item instanceof AutoFeederRate) {
            // 返回全自动馈线覆盖率
            return ((AutoFeederRate) item).getRate();
        } else if (item instanceof OnlineRate) {
            // 返回终端在线率
            return ((OnlineRate) item).getRate();
        } else if (item instanceof IndicatorStatisticRate) {
            LinkedList<BigDecimal> rateList = new LinkedList<>();
            rateList.add(((IndicatorStatisticRate) item).getFaCoverageRate());// 配电自动化覆盖率
            rateList.add(((IndicatorStatisticRate) item).getContactEquipmentControllableRate()); // 联络开关可控率

            for (BigDecimal bigDecimal : rateList) {
                if (bigDecimal.intValue() > 0) {
                    return bigDecimal;
                }
            }
        } else if (item instanceof IntelDistributeCoverage) {
            return ((IntelDistributeCoverage) item).getRate();
        } else if (item instanceof RtuRreqUnline) {
            return ((RtuRreqUnline) item).getRate();
        } else if (item instanceof DefectArchivingRate) {
            return ((DefectArchivingRate) item).getRate();
        } else if (item instanceof RemoteSuccessRate) {
            return ((RemoteSuccessRate) item).getRate();
        }
        return null;
    }

    // 导出所有率指标到巡检日记中
    public void exportRateToInspectionExcel(String fileName) {
        LinkedHashMap<String, String> area = areaAlisa.getArea();

        LinkedList<RateResult> rateResults = new LinkedList<>();

        String monthDate = DateUtil.format(new Date(), "yyyy-MM");
        String dayDate = DateUtil.format(new Date(), "yyyy-MM-dd");

        //获取从今天开始的之前第七天的日期
        String endDate = DateUtil.format(DateUtil.offset(new Date(), DateField.DAY_OF_MONTH, -7), "yyyy-MM-dd");
        String startDate = DateUtil.format(DateUtil.offset(new Date(), DateField.DAY_OF_MONTH, -1), "yyyy-MM-dd");

        //获取上周一和上周日的日期
        // 获取当前日期
        DateTime currentDate = DateUtil.date();

        // 获取上周一的日期
        String lastMonday = DateUtil.format(DateUtil.offsetDay(DateUtil.beginOfWeek(currentDate, true), -7), "yyyy-MM-dd");

        // 获取上周日的日期
        String lastSunday = DateUtil.format(DateUtil.offsetDay(DateUtil.endOfWeek(currentDate, true), -7), "yyyy-MM-dd");

        // 获取配电自动化覆盖率
        ApiResponse<IndicatorStatisticRate> indicatorStatisticRateApiResponse = getIndicatorStatisticRates(monthDate, "faCoverageRate");
        if (indicatorStatisticRateApiResponse != null) {
            LinkedList<RateResult> indicatorStatisticRate = parseRateData(indicatorStatisticRateApiResponse);
            rateResults.addAll(indicatorStatisticRate);
        }

        // 获取全自动馈线率数据
        ApiResponse<AutoFeederRate> autoFeederRateApiResponse = getAutoFeederRates(monthDate);
        if (autoFeederRateApiResponse != null) {
            LinkedList<RateResult> autoFeederRate = parseRateData(autoFeederRateApiResponse);
            rateResults.addAll(autoFeederRate);
        }

        //获取智能站房覆盖率
        ApiResponse<IntelDistributeCoverage> intelDistributeCoverageApiResponse = getIntelDistributeCoverage(monthDate);
        if (intelDistributeCoverageApiResponse != null) {
            LinkedList<RateResult> indicatorStatisticRate = parseRateData(intelDistributeCoverageApiResponse);
            rateResults.addAll(indicatorStatisticRate);
        }

        //获取联络开关可控率
        ApiResponse<IndicatorStatisticRate> indicatorStatisticRateApiResponse2 = getIndicatorStatisticRates(monthDate, "contactEquipmentControllableRate");
        if (indicatorStatisticRateApiResponse2 != null) {
            LinkedList<RateResult> indicatorStatisticRate = parseRateData(indicatorStatisticRateApiResponse2);
            rateResults.addAll(indicatorStatisticRate);
        }

        // 获取终端频繁掉线率
        ApiResponse<RtuRreqUnline> rtuRreqUnlineApiResponse = getRtuRreqUnline(dayDate, dayDate, dayDate);
        if (rtuRreqUnlineApiResponse != null) {
            LinkedList<RateResult> rtuRreqUnline = parseRateData(rtuRreqUnlineApiResponse);
            rateResults.addAll(rtuRreqUnline);
        }
        // 获取自动化缺陷消除归档率
        ApiResponse<DefectArchivingRate> defectArchivingRateApiResponse = getDefectArchivingRates(lastMonday, lastSunday);
        if (defectArchivingRateApiResponse != null) {
            rateResults.addAll(parseRateData(defectArchivingRateApiResponse));
        }
        // 获取终端在线率数据
        ApiResponse<OnlineRate> onlineRateApiResponse = getOnlineRates(dayDate, String.valueOf(1));
        if (onlineRateApiResponse != null) {
            LinkedList<RateResult> onlineRate = parseRateData(onlineRateApiResponse);
            rateResults.addAll(onlineRate);
        }

        // 获取终端遥控成功率
        ApiResponse<RemoteSuccessRate> remoteSuccessRateApiResponse = getRemoteSuccessRates("0", "0", dayDate, dayDate, dayDate);
        if (remoteSuccessRateApiResponse != null) {
            rateResults.addAll(parseRateData(remoteSuccessRateApiResponse));
        }

        try {
            excelExportUtil.exportRateToInspectionExcel(fileName, area, rateResults);
        } catch (Exception e) {
            e.printStackTrace();
            throw new CustomException(AppHttpCodeEnum.SERVER_ERROR, "率指标数据导出失败：ApiService.exportRateToInspectionExcel");
        }
    }

    // 导出终端遥控成功数据到巡检日记中{总数，成功数，率指标}
    public void exportSuccessToInspectionExcel(String fileName, ApiResponse<RemoteSuccessRate> apiResponse) {
        LinkedHashMap<String, String> area = areaAlisa.getArea();

        try {
            excelExportUtil.exportSuccessToInspectionExcel(fileName, area, apiResponse);
        } catch (Exception e) {
            e.printStackTrace();
            throw new CustomException(AppHttpCodeEnum.SERVER_ERROR, "遥控成功率指标数据导出失败：ApiService.exportSuccessToInspectionExcel");
        }
    }

    //获取mock数据
    public ApiResponse<AutoFeederRate> getAutoFeederMockData() throws JsonProcessingException {
        String jsonData = "{\n" +
                "    \"success\": true,\n" +
                "    \"message\": \"成功\",\n" +
                "    \"dataMap\": {},\n" +
                "    \"data\": [\n" +
                "        {\n" +
                "            \"rate\": 0.8278,\n" +
                "            \"areaId\": \"0\",\n" +
                "            \"areaName\": \"全省\",\n" +
                "            \"areaAliasName\": \"全省\",\n" +
                "            \"parentAreaId\": \"-1\",\n" +
                "            \"areaCode\": \"-1\",\n" +
                "            \"time\": \"2024-12\",\n" +
                "            \"count\": 11477,\n" +
                "            \"totalCount\": 13865,\n" +
                "            \"supplyArea\": -1\n" +
                "        },\n" +
                "        {\n" +
                "            \"rate\": 0.8365,\n" +
                "            \"areaId\": \"3096224743817217\",\n" +
                "            \"areaName\": \"福州市\",\n" +
                "            \"areaAliasName\": \"福州市\",\n" +
                "            \"parentAreaId\": \"0\",\n" +
                "            \"areaCode\": \"0\",\n" +
                "            \"time\": \"2024-12\",\n" +
                "            \"count\": 2149,\n" +
                "            \"totalCount\": 2569,\n" +
                "            \"supplyArea\": -1\n" +
                "        },\n" +
                "        {\n" +
                "            \"rate\": 0.9975,\n" +
                "            \"areaId\": \"3096224760594433\",\n" +
                "            \"areaName\": \"厦门市\",\n" +
                "            \"areaAliasName\": \"厦门市\",\n" +
                "            \"parentAreaId\": \"0\",\n" +
                "            \"areaCode\": \"1\",\n" +
                "            \"time\": \"2024-12\",\n" +
                "            \"count\": 1606,\n" +
                "            \"totalCount\": 1610,\n" +
                "            \"supplyArea\": -1\n" +
                "        },\n" +
                "        {\n" +
                "            \"rate\": 0.7016,\n" +
                "            \"areaId\": \"3096224777371649\",\n" +
                "            \"areaName\": \"泉州市\",\n" +
                "            \"areaAliasName\": \"泉州市\",\n" +
                "            \"parentAreaId\": \"0\",\n" +
                "            \"areaCode\": \"2\",\n" +
                "            \"time\": \"2024-12\",\n" +
                "            \"count\": 2208,\n" +
                "            \"totalCount\": 3147,\n" +
                "            \"supplyArea\": -1\n" +
                "        },\n" +
                "        {\n" +
                "            \"rate\": 0.8233,\n" +
                "            \"areaId\": \"3096224794148865\",\n" +
                "            \"areaName\": \"漳州市\",\n" +
                "            \"areaAliasName\": \"漳州市\",\n" +
                "            \"parentAreaId\": \"0\",\n" +
                "            \"areaCode\": \"3\",\n" +
                "            \"time\": \"2024-12\",\n" +
                "            \"count\": 1281,\n" +
                "            \"totalCount\": 1556,\n" +
                "            \"supplyArea\": -1\n" +
                "        },\n" +
                "        {\n" +
                "            \"rate\": 0.9147,\n" +
                "            \"areaId\": \"3096224810926081\",\n" +
                "            \"areaName\": \"龙岩市\",\n" +
                "            \"areaAliasName\": \"龙岩市\",\n" +
                "            \"parentAreaId\": \"0\",\n" +
                "            \"areaCode\": \"4\",\n" +
                "            \"time\": \"2024-12\",\n" +
                "            \"count\": 933,\n" +
                "            \"totalCount\": 1020,\n" +
                "            \"supplyArea\": -1\n" +
                "        },\n" +
                "        {\n" +
                "            \"rate\": 0.7904,\n" +
                "            \"areaId\": \"3096224827703297\",\n" +
                "            \"areaName\": \"三明市\",\n" +
                "            \"areaAliasName\": \"三明市\",\n" +
                "            \"parentAreaId\": \"0\",\n" +
                "            \"areaCode\": \"5\",\n" +
                "            \"time\": \"2024-12\",\n" +
                "            \"count\": 841,\n" +
                "            \"totalCount\": 1064,\n" +
                "            \"supplyArea\": -1\n" +
                "        },\n" +
                "        {\n" +
                "            \"rate\": 0.7935,\n" +
                "            \"areaId\": \"3096224844480513\",\n" +
                "            \"areaName\": \"宁德市\",\n" +
                "            \"areaAliasName\": \"宁德市\",\n" +
                "            \"parentAreaId\": \"0\",\n" +
                "            \"areaCode\": \"6\",\n" +
                "            \"time\": \"2024-12\",\n" +
                "            \"count\": 826,\n" +
                "            \"totalCount\": 1041,\n" +
                "            \"supplyArea\": -1\n" +
                "        },\n" +
                "        {\n" +
                "            \"rate\": 0.9092,\n" +
                "            \"areaId\": \"3096224861257729\",\n" +
                "            \"areaName\": \"南平市\",\n" +
                "            \"areaAliasName\": \"南平市\",\n" +
                "            \"parentAreaId\": \"0\",\n" +
                "            \"areaCode\": \"7\",\n" +
                "            \"time\": \"2024-12\",\n" +
                "            \"count\": 1011,\n" +
                "            \"totalCount\": 1112,\n" +
                "            \"supplyArea\": -1\n" +
                "        },\n" +
                "        {\n" +
                "            \"rate\": 0.8338,\n" +
                "            \"areaId\": \"3096224878034945\",\n" +
                "            \"areaName\": \"莆田市\",\n" +
                "            \"areaAliasName\": \"莆田市\",\n" +
                "            \"parentAreaId\": \"0\",\n" +
                "            \"areaCode\": \"8\",\n" +
                "            \"time\": \"2024-12\",\n" +
                "            \"count\": 622,\n" +
                "            \"totalCount\": 746,\n" +
                "            \"supplyArea\": -1\n" +
                "        }\n" +
                "    ],\n" +
                "    \"code\": 200\n" +
                "}";

        ObjectMapper objectMapper = new ObjectMapper(); // Jackson 的 ObjectMapper 实例

        return objectMapper.readValue(
                jsonData,
                new TypeReference<ApiResponse<AutoFeederRate>>() {
                }
        );
    }

    public ApiResponse<OnlineRate> getOnlineMockData() throws JsonProcessingException {
        String jsonData = "{\n" +
                "    \"success\": true,\n" +
                "    \"message\": \"成功\",\n" +
                "    \"dataMap\": {},\n" +
                "    \"data\": [\n" +
                "        {\n" +
                "            \"indexId\": null,\n" +
                "            \"rtuId\": null,\n" +
                "            \"id\": null,\n" +
                "            \"rtuIdSet\": \"[]\",\n" +
                "            \"areaId\": \"0\",\n" +
                "            \"alisaName\": \"全省\",\n" +
                "            \"areaName\": \"全省\",\n" +
                "            \"parentAreaId\": null,\n" +
                "            \"rtuKind\": 0,\n" +
                "            \"rtuModel\": 0,\n" +
                "            \"updateTime\": null,\n" +
                "            \"time\": \"2024-12-31\",\n" +
                "            \"totalTime\": 7.96642416E10,\n" +
                "            \"onlineTime\": 7.698253381E10,\n" +
                "            \"onlineNum\": null,\n" +
                "            \"totalNum\": 132091,\n" +
                "            \"rate\": 0.9698131,\n" +
                "            \"timeDimension\": 0,\n" +
                "            \"molecule\": 0.0,\n" +
                "            \"denominator\": 0.0,\n" +
                "            \"unlineNum\": 2916,\n" +
                "            \"subControlAreaName\": null,\n" +
                "            \"companyId\": null,\n" +
                "            \"subControlAreaId\": null,\n" +
                "            \"companyName\": null\n" +
                "        },\n" +
                "        {\n" +
                "            \"indexId\": null,\n" +
                "            \"rtuId\": null,\n" +
                "            \"id\": null,\n" +
                "            \"rtuIdSet\": null,\n" +
                "            \"areaId\": \"3096224743817217\",\n" +
                "            \"alisaName\": \"福州市\",\n" +
                "            \"areaName\": null,\n" +
                "            \"parentAreaId\": \"3096224743817217\",\n" +
                "            \"rtuKind\": 0,\n" +
                "            \"rtuModel\": 0,\n" +
                "            \"updateTime\": null,\n" +
                "            \"time\": \"2024-12-31\",\n" +
                "            \"totalTime\": 1.70911296E10,\n" +
                "            \"onlineTime\": 1.6370944679E10,\n" +
                "            \"onlineNum\": null,\n" +
                "            \"totalNum\": 28386,\n" +
                "            \"rate\": 0.9591525999999999,\n" +
                "            \"timeDimension\": 0,\n" +
                "            \"molecule\": 0.0,\n" +
                "            \"denominator\": 0.0,\n" +
                "            \"unlineNum\": 1074,\n" +
                "            \"subControlAreaName\": null,\n" +
                "            \"companyId\": null,\n" +
                "            \"subControlAreaId\": null,\n" +
                "            \"companyName\": null\n" +
                "        },\n" +
                "        {\n" +
                "            \"indexId\": null,\n" +
                "            \"rtuId\": null,\n" +
                "            \"id\": null,\n" +
                "            \"rtuIdSet\": null,\n" +
                "            \"areaId\": \"3096224760594433\",\n" +
                "            \"alisaName\": \"厦门市\",\n" +
                "            \"areaName\": null,\n" +
                "            \"parentAreaId\": \"3096224760594433\",\n" +
                "            \"rtuKind\": 0,\n" +
                "            \"rtuModel\": 0,\n" +
                "            \"updateTime\": null,\n" +
                "            \"time\": \"2024-12-31\",\n" +
                "            \"totalTime\": 8.3575296E9,\n" +
                "            \"onlineTime\": 8.050221089E9,\n" +
                "            \"onlineNum\": null,\n" +
                "            \"totalNum\": 13828,\n" +
                "            \"rate\": 0.9652574,\n" +
                "            \"timeDimension\": 0,\n" +
                "            \"molecule\": 0.0,\n" +
                "            \"denominator\": 0.0,\n" +
                "            \"unlineNum\": 415,\n" +
                "            \"subControlAreaName\": null,\n" +
                "            \"companyId\": null,\n" +
                "            \"subControlAreaId\": null,\n" +
                "            \"companyName\": null\n" +
                "        },\n" +
                "        {\n" +
                "            \"indexId\": null,\n" +
                "            \"rtuId\": null,\n" +
                "            \"id\": null,\n" +
                "            \"rtuIdSet\": null,\n" +
                "            \"areaId\": \"3096224777371649\",\n" +
                "            \"alisaName\": \"泉州市\",\n" +
                "            \"areaName\": null,\n" +
                "            \"parentAreaId\": \"3096224777371649\",\n" +
                "            \"rtuKind\": 0,\n" +
                "            \"rtuModel\": 0,\n" +
                "            \"updateTime\": null,\n" +
                "            \"time\": \"2024-12-31\",\n" +
                "            \"totalTime\": 1.74263328E10,\n" +
                "            \"onlineTime\": 1.6550535037E10,\n" +
                "            \"onlineNum\": null,\n" +
                "            \"totalNum\": 28886,\n" +
                "            \"rate\": 0.9591599999999999,\n" +
                "            \"timeDimension\": 0,\n" +
                "            \"molecule\": 0.0,\n" +
                "            \"denominator\": 0.0,\n" +
                "            \"unlineNum\": 545,\n" +
                "            \"subControlAreaName\": null,\n" +
                "            \"companyId\": null,\n" +
                "            \"subControlAreaId\": null,\n" +
                "            \"companyName\": null\n" +
                "        },\n" +
                "        {\n" +
                "            \"indexId\": null,\n" +
                "            \"rtuId\": null,\n" +
                "            \"id\": null,\n" +
                "            \"rtuIdSet\": null,\n" +
                "            \"areaId\": \"3096224794148865\",\n" +
                "            \"alisaName\": \"漳州市\",\n" +
                "            \"areaName\": null,\n" +
                "            \"parentAreaId\": \"3096224794148865\",\n" +
                "            \"rtuKind\": 0,\n" +
                "            \"rtuModel\": 0,\n" +
                "            \"updateTime\": null,\n" +
                "            \"time\": \"2024-12-31\",\n" +
                "            \"totalTime\": 7.4089728E9,\n" +
                "            \"onlineTime\": 7.271530797E9,\n" +
                "            \"onlineNum\": null,\n" +
                "            \"totalNum\": 12274,\n" +
                "            \"rate\": 0.9831281,\n" +
                "            \"timeDimension\": 0,\n" +
                "            \"molecule\": 0.0,\n" +
                "            \"denominator\": 0.0,\n" +
                "            \"unlineNum\": 159,\n" +
                "            \"subControlAreaName\": null,\n" +
                "            \"companyId\": null,\n" +
                "            \"subControlAreaId\": null,\n" +
                "            \"companyName\": null\n" +
                "        },\n" +
                "        {\n" +
                "            \"indexId\": null,\n" +
                "            \"rtuId\": null,\n" +
                "            \"id\": null,\n" +
                "            \"rtuIdSet\": null,\n" +
                "            \"areaId\": \"3096224810926081\",\n" +
                "            \"alisaName\": \"龙岩市\",\n" +
                "            \"areaName\": null,\n" +
                "            \"parentAreaId\": \"3096224810926081\",\n" +
                "            \"rtuKind\": 0,\n" +
                "            \"rtuModel\": 0,\n" +
                "            \"updateTime\": null,\n" +
                "            \"time\": \"2024-12-31\",\n" +
                "            \"totalTime\": 7.49844E9,\n" +
                "            \"onlineTime\": 7.324399159E9,\n" +
                "            \"onlineNum\": null,\n" +
                "            \"totalNum\": 12439,\n" +
                "            \"rate\": 0.9791947999999999,\n" +
                "            \"timeDimension\": 0,\n" +
                "            \"molecule\": 0.0,\n" +
                "            \"denominator\": 0.0,\n" +
                "            \"unlineNum\": 189,\n" +
                "            \"subControlAreaName\": null,\n" +
                "            \"companyId\": null,\n" +
                "            \"subControlAreaId\": null,\n" +
                "            \"companyName\": null\n" +
                "        },\n" +
                "        {\n" +
                "            \"indexId\": null,\n" +
                "            \"rtuId\": null,\n" +
                "            \"id\": null,\n" +
                "            \"rtuIdSet\": null,\n" +
                "            \"areaId\": \"3096224827703297\",\n" +
                "            \"alisaName\": \"三明市\",\n" +
                "            \"areaName\": null,\n" +
                "            \"parentAreaId\": \"3096224827703297\",\n" +
                "            \"rtuKind\": 0,\n" +
                "            \"rtuModel\": 0,\n" +
                "            \"updateTime\": null,\n" +
                "            \"time\": \"2024-12-31\",\n" +
                "            \"totalTime\": 5.9602752E9,\n" +
                "            \"onlineTime\": 5.916729687E9,\n" +
                "            \"onlineNum\": null,\n" +
                "            \"totalNum\": 9893,\n" +
                "            \"rate\": 0.9938849999999999,\n" +
                "            \"timeDimension\": 0,\n" +
                "            \"molecule\": 0.0,\n" +
                "            \"denominator\": 0.0,\n" +
                "            \"unlineNum\": 33,\n" +
                "            \"subControlAreaName\": null,\n" +
                "            \"companyId\": null,\n" +
                "            \"subControlAreaId\": null,\n" +
                "            \"companyName\": null\n" +
                "        },\n" +
                "        {\n" +
                "            \"indexId\": null,\n" +
                "            \"rtuId\": null,\n" +
                "            \"id\": null,\n" +
                "            \"rtuIdSet\": null,\n" +
                "            \"areaId\": \"3096224844480513\",\n" +
                "            \"alisaName\": \"宁德市\",\n" +
                "            \"areaName\": null,\n" +
                "            \"parentAreaId\": \"3096224844480513\",\n" +
                "            \"rtuKind\": 0,\n" +
                "            \"rtuModel\": 0,\n" +
                "            \"updateTime\": null,\n" +
                "            \"time\": \"2024-12-31\",\n" +
                "            \"totalTime\": 5.8967136E9,\n" +
                "            \"onlineTime\": 5.797268527E9,\n" +
                "            \"onlineNum\": null,\n" +
                "            \"totalNum\": 9780,\n" +
                "            \"rate\": 0.9841766999999999,\n" +
                "            \"timeDimension\": 0,\n" +
                "            \"molecule\": 0.0,\n" +
                "            \"denominator\": 0.0,\n" +
                "            \"unlineNum\": 131,\n" +
                "            \"subControlAreaName\": null,\n" +
                "            \"companyId\": null,\n" +
                "            \"subControlAreaId\": null,\n" +
                "            \"companyName\": null\n" +
                "        },\n" +
                "        {\n" +
                "            \"indexId\": null,\n" +
                "            \"rtuId\": null,\n" +
                "            \"id\": null,\n" +
                "            \"rtuIdSet\": null,\n" +
                "            \"areaId\": \"3096224861257729\",\n" +
                "            \"alisaName\": \"南平市\",\n" +
                "            \"areaName\": null,\n" +
                "            \"parentAreaId\": \"3096224861257729\",\n" +
                "            \"rtuKind\": 0,\n" +
                "            \"rtuModel\": 0,\n" +
                "            \"updateTime\": null,\n" +
                "            \"time\": \"2024-12-31\",\n" +
                "            \"totalTime\": 5.702112E9,\n" +
                "            \"onlineTime\": 5.601767387E9,\n" +
                "            \"onlineNum\": null,\n" +
                "            \"totalNum\": 9445,\n" +
                "            \"rate\": 0.9851086,\n" +
                "            \"timeDimension\": 0,\n" +
                "            \"molecule\": 0.0,\n" +
                "            \"denominator\": 0.0,\n" +
                "            \"unlineNum\": 81,\n" +
                "            \"subControlAreaName\": null,\n" +
                "            \"companyId\": null,\n" +
                "            \"subControlAreaId\": null,\n" +
                "            \"companyName\": null\n" +
                "        },\n" +
                "        {\n" +
                "            \"indexId\": null,\n" +
                "            \"rtuId\": null,\n" +
                "            \"id\": null,\n" +
                "            \"rtuIdSet\": null,\n" +
                "            \"areaId\": \"3096224878034945\",\n" +
                "            \"alisaName\": \"莆田市\",\n" +
                "            \"areaName\": null,\n" +
                "            \"parentAreaId\": \"3096224878034945\",\n" +
                "            \"rtuKind\": 0,\n" +
                "            \"rtuModel\": 0,\n" +
                "            \"updateTime\": null,\n" +
                "            \"time\": \"2024-12-31\",\n" +
                "            \"totalTime\": 4.322736E9,\n" +
                "            \"onlineTime\": 4.099137448E9,\n" +
                "            \"onlineNum\": null,\n" +
                "            \"totalNum\": 7160,\n" +
                "            \"rate\": 0.9516828999999999,\n" +
                "            \"timeDimension\": 0,\n" +
                "            \"molecule\": 0.0,\n" +
                "            \"denominator\": 0.0,\n" +
                "            \"unlineNum\": 289,\n" +
                "            \"subControlAreaName\": null,\n" +
                "            \"companyId\": null,\n" +
                "            \"subControlAreaId\": null,\n" +
                "            \"companyName\": null\n" +
                "        }\n" +
                "    ],\n" +
                "    \"code\": 200\n" +
                "}";

        ObjectMapper objectMapper = new ObjectMapper(); // Jackson 的 ObjectMapper 实例

        return objectMapper.readValue(
                jsonData,
                new TypeReference<ApiResponse<OnlineRate>>() {
                }
        );
    }




    //    /**
//     * 配电自动化覆盖率
//     * @param monthDate 2024-12
//     * @return ApiResponse对象
//     */
//    public ApiResponse<IndicatorStatisticRate> getDistributionAutoCoverage(String monthDate) {
//        String url = "http://25.86.162.57/gateway/iesweb-rate-calculate/indicatorStatistic/getIndicatorStatisticList?areaId=0&time=" + monthDate;
//
//        return fetchDataAndParse(url, new TypeReference<ApiResponse<IndicatorStatisticRate>>() {
//        }, this::parseIndicatorStatisticRateDate);
//    }

//    /**
//     * 获取全自动馈线投入率
//     * @param monthDate 月份
//     * @return ApiResponse对象
//     */
//    public ApiResponse<AutoFeederRate> getAutoFeederRates(String monthDate) {
//        String url = "http://25.86.162.57/devInfo-gateway/iesweb-rtuinfo/autoFeederCoverageRate/getAutoFeederInvestedRates?areaId=0&monthDate=" + monthDate;
//
//        return fetchDataAndParse(url, new TypeReference<ApiResponse<AutoFeederRate>>() {
//        }, this::parseAutoFeederRateDate);
//    }
//
//    // 获取终端在线率
//    public ApiResponse<OnlineRate> getOnlineRates(String dayDate) {
//        String url = "http://25.86.162.57/gateway/iesweb-rate-calculate/onlineRate/getRtuOnlineWeekRate?parentId=0&date=" + dayDate + "&rtuType=1";
//        return fetchDataAndParse(url, new TypeReference<ApiResponse<OnlineRate>>() {
//        }, this::parseOnlineRateDate);
//    }


    //    // 解析通用覆盖率
//    public ArrayList<RateResult> parseIndicatorStatisticRateDate(ApiResponse<IndicatorStatisticRate> response) {
//        return parseRateData(response, IndicatorStatisticRate::getAreaName);
//    }
//
//    // 解析全自动馈线投入的率
//    public ArrayList<RateResult> parseAutoFeederRateDate(ApiResponse<AutoFeederRate> response) {
//        return parseRateData(response, AutoFeederRate::getAreaName);
//    }
//
//    // 解析终端在线率
//    public ArrayList<RateResult> parseOnlineRateDate(ApiResponse<OnlineRate> response) {
//        return parseRateData(response, OnlineRate::getAlisaName);
//    }
}
