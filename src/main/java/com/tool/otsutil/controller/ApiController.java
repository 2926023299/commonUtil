package com.tool.otsutil.controller;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.tool.otsutil.model.common.ResponseResult;
import com.tool.otsutil.model.dto.ApiDto.ApiResponse;
import com.tool.otsutil.model.dto.ApiDto.AutoFeederRate;
import com.tool.otsutil.model.dto.ApiDto.OnlineRate;
import com.tool.otsutil.service.ApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired
    private ApiService apiService;

    @Value("${excel.file-name}")
    private String fileName;

    @GetMapping("/autoFeederRates")
    public ApiResponse<AutoFeederRate> getAutoFeederRates() {
        String monthDate = DateUtil.format(new DateTime(), "yyyy-MM");

        return apiService.getAutoFeederRates(monthDate);
    }

    @GetMapping("/onlineRates")
    public ApiResponse<OnlineRate> getOnlineRates() {
        String dayDate = DateUtil.format(new DateTime(), "yyyy-MM-dd");

        return apiService.getOnlineRates(dayDate, "1");
    }

    @GetMapping("/exportAllRates")
    public ResponseResult exportRateToInspectionExcel() {
        apiService.exportRateToInspectionExcel(fileName);

        return ResponseResult.okResult("率指标数据导出成功");
    }

    @Scheduled(cron = "0 0 8 * * ?") // 每天凌晨执行一次
    public void AutoExportRateToInspectionExcel() {
        apiService.exportRateToInspectionExcel(fileName);
    }
}