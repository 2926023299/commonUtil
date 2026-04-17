package com.tool.otsutil.model.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class XsTopoEventExportRow {
    @ExcelProperty("序号")
    private Integer sequence;

    @ExcelProperty("时间")
    private String timestamp;

    @ExcelProperty("记录类型")
    private String recordType;

    @ExcelProperty("描述")
    private String description;

    @ExcelProperty("馈线ID")
    private String feederId;

    @ExcelProperty("馈线名称")
    private String feederName;

    @ExcelProperty("设备ID")
    private String deviceId;

    @ExcelProperty("设备名称")
    private String deviceName;

    @ExcelProperty("设备所属馈线")
    private String deviceFeederName;

    @ExcelProperty("量测ID")
    private String measureId;

    @ExcelProperty("分合位状态")
    private String switchStatus;
}
