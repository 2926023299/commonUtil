package com.tool.otsutil.config;

import com.tool.otsutil.service.FileProcessor;
import com.tool.otsutil.util.FileProcessingStatusService;
import com.tool.otsutil.util.UniversalFileMonitorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;

@Configuration
@EnableConfigurationProperties(FileMonitorProperties.class)
@Slf4j
public class FileMonitorAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public FileProcessingStatusService fileProcessingStatusService() {
		return new FileProcessingStatusService();
	}

	/**
	 * 主要的文件监控服务Bean
	 */
	@Bean
	@ConditionalOnMissingBean
	public UniversalFileMonitorService universalFileMonitorService(
			ApplicationEventPublisher eventPublisher,
			FileProcessingStatusService statusService,
			ObjectProvider<List<FileProcessor>> processorsProvider) {

		UniversalFileMonitorService monitorService =
				new UniversalFileMonitorService(eventPublisher, statusService);

		// 注册处理器
		List<FileProcessor> processors = processorsProvider.getIfAvailable(Collections::emptyList);
		processors.forEach(processor -> {
			monitorService.registerProcessor(processor);
			log.debug("注册文件处理器: {}", processor.getName());
		});

		return monitorService;
	}

	/**
	 * 监控服务启动器
	 */
	@Bean
	@ConditionalOnBean(UniversalFileMonitorService.class)
	@ConditionalOnProperty(name = "app.file.monitor.enabled", havingValue = "true", matchIfMissing = true)
	public FileMonitorStarter fileMonitorStarter(
			UniversalFileMonitorService monitorService,
			FileMonitorProperties properties) {

		return new FileMonitorStarter(monitorService, properties);
	}
}

