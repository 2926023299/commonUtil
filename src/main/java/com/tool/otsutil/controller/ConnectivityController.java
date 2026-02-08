package com.tool.otsutil.controller;

import com.tool.otsutil.model.common.AppHttpCodeEnum;
import com.tool.otsutil.model.common.ResponseResult;
import com.tool.otsutil.service.ConnectivityCreate;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/connectivity")
public class ConnectivityController {

    @Autowired
    private ConnectivityCreate connectivityCreate;

    /**
     * 执行连接关系处理
     * 调用ConnectivityCreate的selectConnectivityForAll方法，处理所有连接关系数据
     * @return 执行结果
     */
    @GetMapping("/process-all")
    public ResponseResult processAllConnectivity() {
        log.info("开始执行连接关系处理...");
        
        try {
            connectivityCreate.selectConnectivityForAll();
            log.info("连接关系处理完成");
            return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS.getCode(), "连接关系处理成功");
        } catch (Exception e) {
            log.error("连接关系处理失败", e);
            return ResponseResult.errorResult(AppHttpCodeEnum.SERVER_ERROR, "连接关系处理失败: " + e.getMessage());
        }
    }

    /**
     * 处理Excel文件，将数据写入MySQL
     * @param excelFilePath Excel文件路径
     * @return 处理结果
     */
    @GetMapping("/process-excel")
    public ResponseResult processExcelConnectivity() {

        String excelFilePath1 = "C:\\Users\\29260\\Desktop\\sanming.xlsx";
        String excelFilePath2 = "C:\\Users\\29260\\Desktop\\nanping.xlsx";
        String excelFilePath3 = "C:\\Users\\29260\\Desktop\\qz.xlsx";
        log.info("开始处理Excel文件: {}", excelFilePath1);
        
        try {
            connectivityCreate.processConnectivityExcel(excelFilePath1);
            connectivityCreate.processConnectivityExcel(excelFilePath3);
            log.info("Excel文件处理完成");
            return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS.getCode(), "Excel文件处理成功");
        } catch (Exception e) {
            log.error("Excel文件处理失败", e);
            return ResponseResult.errorResult(AppHttpCodeEnum.SERVER_ERROR, "Excel文件处理失败: " + e.getMessage());
        }
    }
}
