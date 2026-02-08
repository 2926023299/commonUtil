package com.tool.otsutil.service.api;

import cn.hutool.core.util.ObjectUtil;
import com.tool.otsutil.exception.CustomException;
import com.tool.otsutil.model.common.AppHttpCodeEnum;
import com.tool.otsutil.model.dto.ApiDto.*;
import com.tool.otsutil.util.DateUtils;
import com.tool.otsutil.util.ExcelExportUtil;
import com.tool.otsutil.util.RateExtractor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
public class ApiService {
	@Autowired
	private AreaAlisa areaAlisa;

	@Autowired
	private ExcelExportUtil excelExportUtil;

	@Autowired
	private ApiManager apiManager;

	/**
	 * 获取全自动馈线投入率
	 *
	 * @param monthDate 月份
	 * @return ApiResponse对象
	 */
	public ApiResponse<AutoFeederRate> getAutoFeederRates(String monthDate) {
		return apiManager.callApi("autoFeederRates", monthDate);
	}

	// 获取终端在线率
	public ApiResponse<OnlineRate> getOnlineRates(String... params) {
		return apiManager.callApi("onlineRates", params);
	}

	// 获取指标统计,通过params指定rateType类型
	public ApiResponse<IndicatorStatisticRate> getIndicatorStatisticRates(String... params) {
		return apiManager.callApi("indicatorStatisticRates", params);
	}

	// 获取智能站房覆盖率
	public ApiResponse<IntelDistributeCoverage> getIntelDistributeCoverage(String monthDate) {
		return apiManager.callApi("intelDistributeCoverage", monthDate);
	}

	// 获取终端频繁掉线率
	public ApiResponse<RtuRreqUnline> getRtuRreqUnline(String... params) {
		return apiManager.callApi("rtuRreqUnline", params);
	}

	// 获取自动化缺陷消除归档率
	public ApiResponse<DefectArchivingRate> getDefectArchivingRates(String... params) {
		return apiManager.callApi("defectArchivingRate", params);
	}

	// 获取终端遥控成功率
	public ApiResponse<RemoteSuccessRate> getRemoteSuccessRates(String... params) {
		return apiManager.callApi("remoteSuccessRate", params);
	}

	// 通用解析方法
	private <T> LinkedList<RateResult> parseRateData(ApiResponse<T> response, RateExtractor<T> extractor) {
		LinkedList<T> dataList = response.getData();
		if (dataList == null) {
			return new LinkedList<>();
		}

		LinkedList<RateResult> rateResultList = new LinkedList<>();

		// 没获取到数据时的默认兜底数据
		LinkedList<RateResult> defaultData = new LinkedList<>();
		defaultData.add(RateResult.createDefault("3096224743817217", "福州市"));
		defaultData.add(RateResult.createDefault("3096224760594433", "厦门市"));
		defaultData.add(RateResult.createDefault("3096224777371649", "泉州市"));
		defaultData.add(RateResult.createDefault("3096224794148865", "漳州市"));
		defaultData.add(RateResult.createDefault("3096224810926081", "龙岩市"));
		defaultData.add(RateResult.createDefault("3096224827703297", "三明市"));
		defaultData.add(RateResult.createDefault("3096224844480513", "宁德市"));
		defaultData.add(RateResult.createDefault("3096224861257729", "南平市"));
		defaultData.add(RateResult.createDefault("3096224878034945", "莆田市"));

		LinkedList<RateResult> finalRateResultList = rateResultList;
		areaAlisa.getArea().keySet().forEach(key -> {
			for (T item : dataList) {
				if (item == null) {
					continue;
				}

				try {
					String areaId = extractor.getAreaId(item);
					BigDecimal rate = extractor.getRate(item);
					String areaName = areaAlisa.getArea().get(areaId);
					if (!ObjectUtil.equal(areaId, key)) {
						continue;
					}

					if (areaId != null && rate != null) {
						RateResult rateResult = new RateResult(areaId, areaName, rate); // 使用areaId作为areaName
						finalRateResultList.add(rateResult);
					}
				} catch (Exception e) {
					// 记录日志或处理异常
					log.error("错误解析数据: {}", e.getMessage(), e);
				}
			}
		});

		if (rateResultList.isEmpty() || rateResultList.size() < 9) {
			log.warn("接口数据解析失败，使用默认数据");
			rateResultList = defaultData;
		}

		log.info("接口数据解析:{}", rateResultList);

		return rateResultList;
	}

	// 导出所有率指标到巡检日记中
	public void exportRateToInspectionExcel(String fileName) {
		LinkedHashMap<String, String> area = areaAlisa.getArea();

		LinkedList<RateResult> rateResults = new LinkedList<>();

		String monthDate = DateUtils.getCurrentMonth();
		String dayDate = DateUtils.getCurrentDate();

		// 获取从今天开始的之前第七天的日期
		String endDate = DateUtils.getDateBefore(7);
		String startDate = DateUtils.getYesterday();

		// 获取上周一和上周日的日期
		String lastMonday = DateUtils.getLastMonday();
		String lastSunday = DateUtils.getLastSunday();

		// 获取配电自动化覆盖率
		ApiResponse<IndicatorStatisticRate> indicatorStatisticRateApiResponse = getIndicatorStatisticRates(monthDate,
				"faCoverageRate");
		if (indicatorStatisticRateApiResponse.getData() != null) {
			LinkedList<RateResult> indicatorStatisticRate = parseRateData(indicatorStatisticRateApiResponse,
					new RateExtractor.IndicatorStatisticRateExtractor("faCoverageRate"));
			rateResults.addAll(indicatorStatisticRate);
		}

		// 获取全自动馈线率数据
		ApiResponse<AutoFeederRate> autoFeederRateApiResponse = getAutoFeederRates(monthDate);
		if (autoFeederRateApiResponse.getData() != null) {
			LinkedList<RateResult> autoFeederRate = parseRateData(autoFeederRateApiResponse,
					new RateExtractor.AutoFeederRateExtractor());
			rateResults.addAll(autoFeederRate);
		}

		// 获取智能站房覆盖率
		ApiResponse<IntelDistributeCoverage> intelDistributeCoverageApiResponse = getIntelDistributeCoverage(monthDate);
		if (intelDistributeCoverageApiResponse.getData() != null) {
			LinkedList<RateResult> indicatorStatisticRate = parseRateData(intelDistributeCoverageApiResponse,
					new RateExtractor.IntelDistributeCoverageExtractor());
			rateResults.addAll(indicatorStatisticRate);
		}

		// 获取联络开关可控率
		ApiResponse<IndicatorStatisticRate> indicatorStatisticRateApiResponse2 = getIndicatorStatisticRates(monthDate,
				"contactEquipmentControllableRate");
		if (indicatorStatisticRateApiResponse2.getData() != null) {
			LinkedList<RateResult> indicatorStatisticRate = parseRateData(indicatorStatisticRateApiResponse2,
					new RateExtractor.IndicatorStatisticRateExtractor("contactEquipmentControllableRate"));
			rateResults.addAll(indicatorStatisticRate);
		}

		// 获取终端频繁掉线率
		ApiResponse<RtuRreqUnline> rtuRreqUnlineApiResponse = getRtuRreqUnline(dayDate, dayDate, dayDate);
		if (rtuRreqUnlineApiResponse.getData() != null) {
			LinkedList<RateResult> rtuRreqUnline = parseRateData(rtuRreqUnlineApiResponse,
					new RateExtractor.RtuRreqUnlineExtractor());
			rateResults.addAll(rtuRreqUnline);
		}
		// 获取自动化缺陷消除归档率
		ApiResponse<DefectArchivingRate> defectArchivingRateApiResponse = getDefectArchivingRates(lastMonday,
				lastSunday);
		if (defectArchivingRateApiResponse.getData() != null) {
			rateResults.addAll(
					parseRateData(defectArchivingRateApiResponse, new RateExtractor.DefectArchivingRateExtractor()));
		}
		// 获取终端在线率数据
		ApiResponse<OnlineRate> onlineRateApiResponse = getOnlineRates(dayDate, String.valueOf(1));
		if (onlineRateApiResponse.getData() != null) {
			LinkedList<RateResult> onlineRate = parseRateData(onlineRateApiResponse,
					new RateExtractor.OnlineRateExtractor());
			rateResults.addAll(onlineRate);
		}

		// 获取终端遥控成功率
		ApiResponse<RemoteSuccessRate> remoteSuccessRateApiResponse = getRemoteSuccessRates("0", "0", dayDate, dayDate,
				dayDate);
		if (remoteSuccessRateApiResponse.getData() != null) {
			rateResults.addAll(
					parseRateData(remoteSuccessRateApiResponse, new RateExtractor.RemoteSuccessRateExtractor()));
		}

		try {
			excelExportUtil.exportRateToInspectionExcel(fileName, area, rateResults);
		} catch (Exception e) {
			log.error("率指标数据导出失败：ApiService.exportRateToInspectionExcel", e);
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
			throw new CustomException(AppHttpCodeEnum.SERVER_ERROR,
					"遥控成功率指标数据导出失败：ApiService.exportSuccessToInspectionExcel");
		}
	}
}