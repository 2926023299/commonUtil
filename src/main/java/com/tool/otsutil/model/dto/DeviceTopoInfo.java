package com.tool.otsutil.model.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class DeviceTopoInfo {
    @ExcelProperty("设备id")
    private String id;
    
    @ExcelProperty("设备名称")
    private String deviceName;
    
    @ExcelProperty("馈线名称")
    private String feederName;
    
    @ExcelProperty("备注")
    private String feederChange;
}