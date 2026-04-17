package com.tool.otsutil.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tool.otsutil.model.common.AppHttpCodeEnum;
import com.tool.otsutil.model.common.ResponseResult;
import com.tool.otsutil.model.dto.inspection.InspectionPage;
import com.tool.otsutil.model.dto.inspection.JavaInspectionListRequest;
import com.tool.otsutil.model.dto.inspection.ServerHistoryRequest;
import com.tool.otsutil.model.dto.inspection.ServerInspectionListRequest;
import com.tool.otsutil.model.entity.InspectionTable;
import com.tool.otsutil.model.vo.inspection.DashboardSummaryView;
import com.tool.otsutil.model.vo.inspection.JavaInspectionDetailView;
import com.tool.otsutil.model.vo.inspection.JavaInspectionView;
import com.tool.otsutil.model.vo.inspection.PageResponse;
import com.tool.otsutil.model.vo.inspection.ServerInspectionView;
import com.tool.otsutil.model.vo.inspection.TopologySummaryView;
import com.tool.otsutil.service.InspectionImpl.InspectionService;
import com.tool.otsutil.service.InspectionImpl.InspectionQueryService;
import com.tool.otsutil.service.InspectionImpl.InspectionTableService;
import com.tool.otsutil.service.InspectionImpl.TuMoStatisticsService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/Inspection")
@EnableScheduling // 启用定时任务
public class InspectionController {

	@Value("${excel.file-name}")
	private String fileName;

	@Autowired
	private InspectionService inspectionService;

	@Autowired
	private TuMoStatisticsService tuMoStatisticsService;

	@Autowired
	private InspectionTableService inspectionTableService;

	@Autowired
	private InspectionQueryService inspectionQueryService;


	/**
	 * 获取巡检清单结果，数据库分页查询
	 * @param inspectionTable
	 * @return
	 * @throws IOException
	 */
	@PostMapping("/run")
	public ResponseResult runInspection(@RequestBody InspectionPage inspectionTable) throws IOException {
		Page<InspectionTable> paginatedInspectionTable = inspectionTableService.getPaginatedInspectionTable(inspectionTable);
		return ResponseResult.okResult(paginatedInspectionTable);
	}

	//根据IP和更新时间获取单条的检查记录
	@GetMapping("/getInspectionByIp")
	public ResponseResult<InspectionTable> getInspectionByIp(@RequestParam String ip, @RequestParam String update) {
		return ResponseResult.okResult(inspectionTableService.getInspectionByIp(ip, update));
	}

	//写入mysql巡检记录
	@GetMapping("/saveInspection")
	public ResponseResult saveInspection() {
		inspectionService.performInspection();
		return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS.getCode(), "保存成功");
	}

	@PostMapping("/server/run")
	public ResponseResult triggerServerInspection() {
		inspectionService.performInspection();
		return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS.getCode(), "巡检已执行");
	}

	//根据IP获取该ip的历史查询记录
	@PostMapping("/getInspectionHistoryByIp")
	public ResponseResult<Page<InspectionTable>> getInspectionPageByIp(@RequestBody InspectionPage inspectionPage) {
		Page<InspectionTable> inspectionPageByIp = inspectionTableService.getInspectionPageByIp(inspectionPage);
		return ResponseResult.okResult(inspectionPageByIp);
	}

	@GetMapping("/returnStatisticsResult")
	public ResponseResult returnStatisticsResult() {

		//获取前一天的日期，格式为‘20241228’
		// 获取当前日期
		LocalDate currentDate = LocalDate.now();
		// 获取前一天的日期
		LocalDate previousDate = currentDate.minusDays(1);
		// 定义日期格式
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
		// 格式化日期
		String date = previousDate.format(formatter);


		// 获取中压和低压统计数据
		Map<String, Integer> zyStatistics = tuMoStatisticsService.getZyStatistics(date);
		Map<String, Integer> dyStatistics = tuMoStatisticsService.getDyStatistics(date);

		// 返回统计结果
		List<Map<String, Map<String, Integer>>> result = new LinkedList<>();
		//  创建一个Map对象，用于存储中压和低压的统计数据
		Map<String, Map<String, Integer>> statisticsResult = new HashMap<>();
		statisticsResult.put("中压", zyStatistics);
		statisticsResult.put("低压", dyStatistics);
		result.add(statisticsResult);

		return ResponseResult.okResult(result);
	}

	@GetMapping("/dashboard")
	public ResponseResult<DashboardSummaryView> getDashboardSummary() {
		return ResponseResult.okResult(inspectionQueryService.getDashboardSummary());
	}

	@PostMapping("/server/list")
	public ResponseResult<PageResponse<ServerInspectionView>> getServerInspectionList(@RequestBody ServerInspectionListRequest request) {
		return ResponseResult.okResult(inspectionQueryService.getServerInspectionList(request));
	}

	@GetMapping("/server/detail")
	public ResponseResult<ServerInspectionView> getServerInspectionDetail(@RequestParam String ip, @RequestParam String updateTime) {
		return ResponseResult.okResult(inspectionQueryService.getServerInspectionDetail(ip, updateTime));
	}

	@PostMapping("/server/history")
	public ResponseResult<PageResponse<ServerInspectionView>> getServerInspectionHistory(@RequestBody ServerHistoryRequest request) {
		return ResponseResult.okResult(inspectionQueryService.getServerInspectionHistory(request));
	}

	@PostMapping("/java/list")
	public ResponseResult<PageResponse<JavaInspectionView>> getJavaInspectionList(@RequestBody JavaInspectionListRequest request) {
		return ResponseResult.okResult(inspectionQueryService.getJavaInspectionList(request));
	}

	@GetMapping("/java/detail")
	public ResponseResult<JavaInspectionDetailView> getJavaInspectionDetail(@RequestParam String ip) {
		return ResponseResult.okResult(inspectionQueryService.getJavaInspectionDetail(ip));
	}

	@GetMapping("/topology/summary")
	public ResponseResult<TopologySummaryView> getTopologySummary() {
		return ResponseResult.okResult(inspectionQueryService.getTopologySummary());
	}


	// 导出服务器巡检结果到excel
	@GetMapping("/exportServer")
	public ResponseResult exportInspectionResults() throws Exception {
		inspectionService.exportInspectionToExcel(fileName, "资源巡检(每小时)");

		return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS.getCode(), "导出成功\n");
	}

	@GetMapping("/server/export")
	public ResponseResult exportServerInspectionResults() throws Exception {
		inspectionService.exportInspectionToExcel(fileName, "资源巡检(每小时)");
		return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS.getCode(), "服务器巡检结果已导出");
	}

	@Scheduled(cron = "0 12 10 * * ?") // 每天早上8点12分执行
	public void exportInspection() throws Exception {
		inspectionService.exportInspectionToExcel(fileName, "服务器资源占用巡检");
	}

	@Scheduled(cron = "0 0 0/6 * * ?") // 每6小时执行一次
	public void exportInspectionByHour() throws Exception {
		inspectionService.exportInspectionToExcel(fileName, "资源巡检(每小时)");
	}

	@Scheduled(cron = "0 0 8,17 * * ?") // 每天上午8点和下午5点执行
	public void exportJavaInspectionByScheduled() throws Exception {
		inspectionService.exportJavaInspectionToExcel(fileName);
	}

	@GetMapping("/exportJavaInspection")
	public void exportJavaInspection() throws Exception {
		inspectionService.exportJavaInspectionToExcel(fileName);
	}

	@GetMapping("/java/export")
	public ResponseResult exportJavaInspectionResult() throws Exception {
		inspectionService.exportJavaInspectionToExcel(fileName);
		return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS.getCode(), "Java巡检结果已导出");
	}

	@Scheduled(cron = "0 0 2 * * ?") // 每天凌晨执行一次
	public void exportStatistics() {

		//获取前一天的日期，格式为‘20241228’
		// 获取当前日期
		LocalDate currentDate = LocalDate.now();
		// 获取前一天的日期
		LocalDate previousDate = currentDate.minusDays(1);
		// 定义日期格式
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
		// 格式化日期
		String date = previousDate.format(formatter);

		// 获取中压和低压统计数据
		Map<String, Integer> zyStatistics = tuMoStatisticsService.getZyStatistics(date);
		Map<String, Integer> dyStatistics = tuMoStatisticsService.getDyStatistics(date);

		// 导出到 Excel
		tuMoStatisticsService.exportStatisticsToExcel(fileName, zyStatistics, dyStatistics);
	}


	// 导出图模统计结果到excel
	@GetMapping("/exportTuMo")
	public ResponseResult exportStatisticsResult() {

		//获取前一天的日期，格式为‘20241228’
		// 获取当前日期
		LocalDate currentDate = LocalDate.now();
		// 获取前一天的日期
		LocalDate previousDate = currentDate.minusDays(1);
		// 定义日期格式
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
		// 格式化日期
		String date = previousDate.format(formatter);

		// 获取中压和低压统计数据
		Map<String, Integer> zyStatistics = tuMoStatisticsService.getZyStatistics(date);
		Map<String, Integer> dyStatistics = tuMoStatisticsService.getDyStatistics(date);

		// 导出到 Excel
		tuMoStatisticsService.exportStatisticsToExcel(fileName, zyStatistics, dyStatistics);

		return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS.getCode(), "导出成功");
	}

	@GetMapping("/topology/export")
	public ResponseResult exportTopologyStatisticsResult() {

		LocalDate currentDate = LocalDate.now();
		LocalDate previousDate = currentDate.minusDays(1);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
		String date = previousDate.format(formatter);

		Map<String, Integer> zyStatistics = tuMoStatisticsService.getZyStatistics(date);
		Map<String, Integer> dyStatistics = tuMoStatisticsService.getDyStatistics(date);

		tuMoStatisticsService.exportStatisticsToExcel(fileName, zyStatistics, dyStatistics);

		return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS.getCode(), "图模统计结果已导出");
	}
}
