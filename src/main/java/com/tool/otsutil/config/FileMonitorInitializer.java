package com.tool.otsutil.config;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tool.otsutil.util.UniversalFileMonitorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class FileMonitorInitializer {

//	@Autowired
//	private UniversalFileMonitorService monitorService;
//
//	@Value("${app.file.monitor.configs:}")
//	private String monitorConfigsJson;
//
//	@EventListener(ApplicationReadyEvent.class)
//	public void initializeMonitors() {
//		log.info("初始化文件监控配置");
//
//		// 从配置文件加载监控配置
//		loadMonitorConfigsFromProperties();
//
//		// 可以在这里添加默认监控配置
//		setupDefaultMonitors();
//	}
//
//	private void loadMonitorConfigsFromProperties() {
//		if (StringUtils.isNotEmpty(monitorConfigsJson)) {
//			try {
//				ObjectMapper mapper = new ObjectMapper();
//				List<Map<String, Object>> configs = mapper.readValue(
//						monitorConfigsJson, new TypeReference<List<Map<String, Object>>>() {});
//
//				for (Map<String, Object> configMap : configs) {
//					FileMonitorConfig config = createConfigFromMap(configMap);
//					monitorService.startMonitoring(config);
//				}
//
//			} catch (Exception e) {
//				log.error("解析监控配置失败", e);
//			}
//		}
//	}
//
//	private FileMonitorConfig createConfigFromMap(Map<String, Object> configMap) {
//		return FileMonitorConfig.builder()
//				.id((String) configMap.get("id"))
//				.directory((String) configMap.get("directory"))
//				.filePattern((String) configMap.getOrDefault("filePattern", "*"))
//				.recursive(Boolean.parseBoolean(String.valueOf(configMap.getOrDefault("recursive", "false"))))
//				.checkInterval(Integer.parseInt(String.valueOf(configMap.getOrDefault("checkInterval", "30"))))
//				.maxConcurrent(Integer.parseInt(String.valueOf(configMap.getOrDefault("maxConcurrent", "5"))))
//				.stabilityThreshold(Long.parseLong(String.valueOf(configMap.getOrDefault("stabilityThreshold", "10000"))))
//				.build();
//	}
//
//	private void setupDefaultMonitors() {
//		// 添加一些默认监控配置
//		FileMonitorConfig csvMonitor = FileMonitorConfig.builder()
//				.id("csv-upload")
//				.directory("/data/uploads/csv")
//				.filePattern("*.csv")
//				.checkInterval(10)
//				.stabilityThreshold(15000)
//				.build();
//
//		FileMonitorConfig jsonMonitor = FileMonitorConfig.builder()
//				.id("json-upload")
//				.directory("/data/uploads/json")
//				.filePattern("*.json")
//				.checkInterval(15)
//				.stabilityThreshold(10000)
//				.build();
//
//		monitorService.startMonitoring(csvMonitor);
//		monitorService.startMonitoring(jsonMonitor);
//	}
}
