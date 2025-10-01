package com.tool.otsutil.config;

import lombok.Builder;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 文件监控配置
 */
@Data
@Builder
public class FileMonitorConfig {
	private String id; // 监控配置ID
	private String directory; // 监控目录
	private String filePattern; // 文件匹配模式，如 "*.csv", "*.json"
	private boolean recursive; // 是否递归子目录
	private int checkInterval; // 检查间隔(秒)
	private int maxConcurrent; // 最大并发处理数
	private long stabilityThreshold; // 文件稳定阈值(毫秒)
	private Map<String, Object> customProperties; // 自定义属性
}

