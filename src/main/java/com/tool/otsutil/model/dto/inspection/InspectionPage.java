package com.tool.otsutil.model.dto.inspection;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InspectionPage {
	private String IP; // IP地址
	private String CPU_USAGE; // CPU使用率
	private String MEMORY_USAGE_RATE; // 内存使用率
	private String MEMORY_USAGE; // 内存使用量
	private String MEMORY_TOTAL; // 内存总量
	private String DISK_USAGE_RATE; // 磁盘使用率
	private String DISK_USAGE; // 磁盘使用量
	private String DISK_TOTAL; // 磁盘总量
	private String THREAD_COUNT; // 线程数
	private String JAVA_PROCESSES; // Java进程信息

	private String SECOND_DISK_USAGE_RATE;
	private String SECOND_DISK_USAGE;
	private String SECOND_DISK_TOTAL;

	private String THIRD_DISK_USAGE_RATE;
	private String THIRD_DISK_USAGE;
	private String THIRD_DISK_TOTAL;

	//更新时间
	private String updateTime;

	private Integer Status; // 状态信息

	private String description;

	private int page;
	private int pageSize;
}
