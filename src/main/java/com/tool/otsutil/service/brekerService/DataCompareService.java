package com.tool.otsutil.service.brekerService;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import com.alibaba.excel.write.metadata.style.WriteFont;
import com.alibaba.excel.write.style.HorizontalCellStyleStrategy;
import com.tool.otsutil.model.entity.BreakerEnergyData;

import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据对比服务
 */
@Service
public class DataCompareService {

    @Autowired
    private BreakerEnergyDataService breakerEnergyDataService;

    /**
     * 对比两天数据并输出到Excel
     * @param date1 日期1（格式：yyyy-MM-dd）
     * @param date2 日期2（格式：yyyy-MM-dd）
     * @param outputPath 输出Excel路径
     */
    public void compareDataAndExportExcel(String date1, String date2, String outputPath) {
        // 查询两天的数据
        List<BreakerEnergyData> data1List = breakerEnergyDataService.list();
        List<BreakerEnergyData> data2List = breakerEnergyDataService.list();

        // 按开关标识和数据类型分组
        Map<String, BreakerEnergyData> data1Map = data1List.stream()
                .collect(Collectors.toMap(
                        d -> d.getBreakerId() + "_" + d.getDataType(),
                        d -> d
                ));

        Map<String, BreakerEnergyData> data2Map = data2List.stream()
                .collect(Collectors.toMap(
                        d -> d.getBreakerId() + "_" + d.getDataType(),
                        d -> d
                ));

        // 找出所有唯一的键
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(data1Map.keySet());
        allKeys.addAll(data2Map.keySet());

        // 准备Excel数据
        List<CompareResult> resultList = new ArrayList<>();
        for (String key : allKeys) {
            BreakerEnergyData d1 = data1Map.get(key);
            BreakerEnergyData d2 = data2Map.get(key);

            if (d1 != null && d2 != null) {
                CompareResult result = new CompareResult();
                result.setBreakerId(d1.getBreakerId());
                result.setFileTime1(d1.getFileTime());
                result.setFileTime2(d2.getFileTime());
                result.setDataType(d1.getDataType());
                result.setDataValue1(d1.getDataValue());
                result.setDataValue2(d2.getDataValue());
                // 计算差值
                double diff = d2.getDataValue() - d1.getDataValue();
                result.setDiffValue(diff);
                // 判断是否异常
                if (diff < 1 || diff > 50000) {
                    result.setIsException("异常");
                } else {
                    result.setIsException("正常");
                }
                resultList.add(result);
            }
        }

        // 导出到Excel
        writeExcel(resultList, outputPath);
    }

    /**
     * 将数据写入Excel
     * @param dataList 数据列表
     * @param outputPath 输出路径
     */
    private void writeExcel(List<CompareResult> dataList, String outputPath) {
        // 设置表头样式
        WriteCellStyle headStyle = new WriteCellStyle();
        WriteFont headFont = new WriteFont();
        headFont.setFontHeightInPoints((short) 12);
        headFont.setBold(true);
        headStyle.setWriteFont(headFont);
        headStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        headStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());

        // 设置内容样式
        WriteCellStyle contentStyle = new WriteCellStyle();
        WriteFont contentFont = new WriteFont();
        contentFont.setFontHeightInPoints((short) 11);
        contentStyle.setWriteFont(contentFont);
        contentStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);

        HorizontalCellStyleStrategy styleStrategy = new HorizontalCellStyleStrategy(headStyle, contentStyle);

        // 写入Excel
        EasyExcel.write(outputPath, CompareResult.class)
                .registerWriteHandler(styleStrategy)
                .sheet("数据对比结果")
                .doWrite(dataList);
    }

    /**
     * 数据对比结果类
     */
    public static class CompareResult {
        // 开关标识
        private String breakerId;
        // 文件时间1
        private Date fileTime1;
        // 文件时间2
        private Date fileTime2;
        // 数据类型
        private Integer dataType;
        // 数据值1
        private Double dataValue1;
        // 数据值2
        private Double dataValue2;
        // 相减的数据值
        private Double diffValue;
        // 是否异常
        private String isException;

        // Getters and Setters
        public String getBreakerId() {
            return breakerId;
        }

        public void setBreakerId(String breakerId) {
            this.breakerId = breakerId;
        }

        public Date getFileTime1() {
            return fileTime1;
        }

        public void setFileTime1(Date fileTime1) {
            this.fileTime1 = fileTime1;
        }

        public Date getFileTime2() {
            return fileTime2;
        }

        public void setFileTime2(Date fileTime2) {
            this.fileTime2 = fileTime2;
        }

        public Integer getDataType() {
            return dataType;
        }

        public void setDataType(Integer dataType) {
            this.dataType = dataType;
        }

        public Double getDataValue1() {
            return dataValue1;
        }

        public void setDataValue1(Double dataValue1) {
            this.dataValue1 = dataValue1;
        }

        public Double getDataValue2() {
            return dataValue2;
        }

        public void setDataValue2(Double dataValue2) {
            this.dataValue2 = dataValue2;
        }

        public Double getDiffValue() {
            return diffValue;
        }

        public void setDiffValue(Double diffValue) {
            this.diffValue = diffValue;
        }

        public String getIsException() {
            return isException;
        }

        public void setIsException(String isException) {
            this.isException = isException;
        }
    }
}