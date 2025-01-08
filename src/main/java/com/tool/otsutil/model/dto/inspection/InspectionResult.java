package com.tool.otsutil.model.dto.inspection;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class InspectionResult {

    private Map<String, String> ResultInfo; // 存储结果的map<常量, 数值>

    private String ip = "";
    private String cpuUsage= "";
    //内存
    private String memoryUsageRate= "";
    private String memoryUsage= "";
    private String memoryTotal= "";
    //硬盘
    private String diskUsageRate= "";
    private String diskUsage= "";
    private String diskTotal= "";
    //线程数
    private String threadCount= "";
    //java进程
    private String javaProcesses= "";


    public InspectionResult(){
        ResultInfo = new java.util.HashMap<>();
        ResultInfo.put(InspectionCommon.IP, ip);
        ResultInfo.put(InspectionCommon.JAVA_PROCESSES, javaProcesses);
        ResultInfo.put(InspectionCommon.MEMORY_TOTAL, memoryTotal);
        ResultInfo.put(InspectionCommon.DISK_TOTAL, diskTotal);
        ResultInfo.put(InspectionCommon.THREAD_COUNT, threadCount);
        ResultInfo.put(InspectionCommon.DISK_USAGE_RATE, diskUsageRate);
        ResultInfo.put(InspectionCommon.DISK_USAGE, diskUsage);
        ResultInfo.put(InspectionCommon.MEMORY_USAGE_RATE, memoryUsageRate);
        ResultInfo.put(InspectionCommon.MEMORY_USAGE, memoryUsage);
        ResultInfo.put(InspectionCommon.CPU_USAGE, cpuUsage);
    }


    public InspectionResult(Map<String, String> ResultInfo) {
        this.ResultInfo = ResultInfo;
    }


}
