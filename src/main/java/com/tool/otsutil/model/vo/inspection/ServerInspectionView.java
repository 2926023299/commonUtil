package com.tool.otsutil.model.vo.inspection;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ServerInspectionView {
    private String ip;
    private String updateTime;
    private Integer status;
    private String description;
    private String cpuUsage;
    private String memoryUsage;
    private String memoryTotal;
    private String memoryUsageRate;
    private String diskUsage;
    private String diskTotal;
    private String diskUsageRate;
    private String secondDiskUsage;
    private String secondDiskTotal;
    private String secondDiskUsageRate;
    private String thirdDiskUsage;
    private String thirdDiskTotal;
    private String thirdDiskUsageRate;
    private String threadCount;
    private List<String> javaProcesses = new ArrayList<String>();
    private int javaProcessCount;
}
