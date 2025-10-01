package com.tool.otsutil.config;

import com.tool.otsutil.util.UniversalFileMonitorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
/**
 * 监控服务启动器
 */
@Slf4j
public class FileMonitorStarter {

	private final UniversalFileMonitorService monitorService;
	private final FileMonitorProperties properties;

	public FileMonitorStarter(UniversalFileMonitorService monitorService,
							  FileMonitorProperties properties) {
		this.monitorService = monitorService;
		this.properties = properties;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void startMonitors() {
		if (!properties.isEnabled()) {
			log.info("文件监控服务已禁用");
			return;
		}

		log.info("启动文件监控服务，共 {} 个配置", properties.getConfigs().size());

		for (FileMonitorProperties.MonitorConfig configProps : properties.getConfigs()) {
			FileMonitorConfig config = convertToMonitorConfig(configProps);

			boolean success = monitorService.startMonitoring(config);
			if (success) {
				log.info("成功启动监控: {} -> {}", config.getId(), config.getDirectory());
			} else {
				log.error("启动监控失败: {}", config.getId());
			}
		}
	}

	private FileMonitorConfig convertToMonitorConfig(FileMonitorProperties.MonitorConfig props) {
		return FileMonitorConfig.builder()
				.id(props.getId())
				.directory(props.getDirectory())
				.filePattern(props.getFilePattern())
				.recursive(props.isRecursive())
				.checkInterval(props.getCheckInterval() != null ?
						props.getCheckInterval() : properties.getDefaultCheckInterval())
				.maxConcurrent(props.getMaxConcurrent() != null ?
						props.getMaxConcurrent() : properties.getDefaultMaxConcurrent())
				.stabilityThreshold(props.getStabilityThreshold() != null ?
						props.getStabilityThreshold() : properties.getDefaultStabilityThreshold())
				.build();
	}
}
