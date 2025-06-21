package com.tool.otsutil.model.dto.inspection;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
public class InspectionResult {

    private Map<String, String> ResultInfo; // 存储结果的map<常量, 数值>

    public InspectionResult(){
        ResultInfo = new java.util.HashMap<>();
        ResultInfo.put(InspectionCommon.IP, "");
        ResultInfo.put(InspectionCommon.JAVA_PROCESSES, "");
        ResultInfo.put(InspectionCommon.MEMORY_TOTAL, "");
        ResultInfo.put(InspectionCommon.DISK_TOTAL, "");
        ResultInfo.put(InspectionCommon.THREAD_COUNT, "");
        ResultInfo.put(InspectionCommon.DISK_USAGE_RATE, "");
        ResultInfo.put(InspectionCommon.DISK_USAGE, "");
        ResultInfo.put(InspectionCommon.MEMORY_USAGE_RATE, "");
        ResultInfo.put(InspectionCommon.MEMORY_USAGE, "");
        ResultInfo.put(InspectionCommon.CPU_USAGE, "");
    }


    public InspectionResult(Map<String, String> ResultInfo) {
        this.ResultInfo = ResultInfo;
    }

}
