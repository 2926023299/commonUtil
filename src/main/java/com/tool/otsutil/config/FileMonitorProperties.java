package com.tool.otsutil.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "app.file.monitor")
public class FileMonitorProperties {
	private boolean enabled = true;
	private List<MonitorConfig> configs = new ArrayList<>();
	private int defaultCheckInterval = 30;
	private int defaultMaxConcurrent = 5;
	private long defaultStabilityThreshold = 10000L;

	@Data
	public static class MonitorConfig {
		private String id;
		private String directory;
		private String filePattern = "*";
		private boolean recursive = false;
		private Integer checkInterval;
		private Integer maxConcurrent;
		private Long stabilityThreshold;
	}
}
