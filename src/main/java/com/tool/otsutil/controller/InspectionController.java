package com.tool.otsutil.controller;

import com.tool.otsutil.model.common.AppHttpCodeEnum;
import com.tool.otsutil.model.common.ResponseResult;
import com.tool.otsutil.model.dto.inspection.InspectionResult;
import com.tool.otsutil.service.InspectionService;
import com.tool.otsutil.service.TuMoStatisticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

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


    @GetMapping("/run")
    public ResponseEntity<Map<String, InspectionResult>> runInspection() {
        Map<String, InspectionResult> stringInspectionResultMap = inspectionService.performInspection();
        return ResponseEntity.ok(stringInspectionResultMap);
    }

    @Scheduled(cron = "0 0 1 * * ?") // 每天凌晨执行一次
    public void exportInspection() throws Exception {
        inspectionService.exportInspectionToExcel(fileName);
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

    // 导出服务器巡检结果到excel
    @GetMapping("/exportServer")
    public ResponseResult exportInspectionResults() throws Exception {
        inspectionService.exportInspectionToExcel(fileName);

        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS.getCode(), "导出成功");
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
}
