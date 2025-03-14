package com.tool.otsutil.model.dto.inspection;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
public class InspectionCommon {
    // 待检测项
    public static final String IP = "IP";
    public static final String CPU_USAGE = "CPU_USAGE";
    public static final String MEMORY_USAGE_RATE = "MEMORY_USAGE_RATE";
    public static final String MEMORY_USAGE = "MEMORY_USAGE";
    public static final String MEMORY_TOTAL = "MEMORY_TOTAL";
    public static final String DISK_USAGE_RATE = "DISK_USAGE_RATE";
    public static final String DISK_USAGE = "DISK_USAGE";
    public static final String DISK_TOTAL = "DISK_TOTAL";
    public static final String THREAD_COUNT = "THREAD_COUNT";
    public static final String JPS_PATH = "JPS_PATH";
    public static final String JAVA_PROCESSES = "JAVA_PROCESSES";

    public static final String SECOND_DISK_USAGE_RATE = "SECOND_DISK_USAGE_RATE";
    public static final String SECOND_DISK_USAGE = "SECOND_DISK_USAGE";
    public static final String SECOND_DISK_TOTAL = "SECOND_DISK_TOTAL";

    public static final String THIRD_DISK_USAGE_RATE = "THIRD_DISK_USAGE_RATE";
    public static final String THIRD_SECOND_DISK_USAGE = "THIRD_DISK_USAGE";
    public static final String THIRD_SECOND_DISK_TOTAL = "THIRD_DISK_TOTAL";


    public static final String SECOND_DISK_USAGE_RATE_209 = "SECOND_DISK_USAGE_RATE_209";
    public static final String SECOND_DISK_USAGE_209 = "SECOND_DISK_USAGE_209";
    public static final String SECOND_DISK_TOTAL_209 = "SECOND_DISK_TOTAL_209";
    //显示项
//    public static final String DISPLAY_CPU_USAGE = "CPU使用率";
//    public static final String DISPLAY_MEMORY_USAGE_RATE = "内存使用率";
//    public static final String DISPLAY_MEMORY_USAGE = "内存使用量"; // 16GB/32GB
//    public static final String DISPLAY_DISK_USAGE_RATE = "磁盘使用率";
//    public static final String DISPLAY_DISK_USAGE = "磁盘使用量"; // 100GB/200GB
//    public static final String DISPLAY_THREAD_COUNT = "线程数";
//    public static final String DISPLAY_JAVA_PROCESSES = "Java进程";
}
