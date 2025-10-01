package com.tool.otsutil.service.impl;

import com.tool.otsutil.model.fileProcess.FileEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;

@Slf4j
@Component
public class JarDeploymentEventListener {

	@Autowired
	private JarDeployProcessor jarDeployProcessor;

	/**
	 * 监听JAR文件上传完成事件并触发部署
	 */
	@EventListener(condition = "#fileEvent.eventType.name() == 'UPLOAD_COMPLETED' && #fileEvent.filePath.endsWith('.jar')")
	public void handleJarUploadCompleted(FileEvent fileEvent) {
		String jarFilePath = fileEvent.getFilePath();
		log.info("[JAR部署] 事件检测到新的JAR文件: {}", jarFilePath);

		// 可以在这里触发JAR包的自动部署逻辑
		//jarDeployProcessor.autoScanAndDeploy(Paths.get(jarFilePath));
	}
}