package com.tool.otsutil.service;

import com.tool.otsutil.config.FileMonitorConfig;
import com.tool.otsutil.model.fileProcess.ProcessingResult;
import com.tool.otsutil.model.fileProcess.FileEvent;

import java.util.Collections;
import java.util.Map; /**
 * 通用文件处理器接口
 */
public interface FileProcessor {
	/**
	 * 处理器名称
	 */
	String getName();

	/**
	 * 支持的文件模式
	 */
	boolean supports(String filePath, boolean isStable);

	/**
	 * 处理文件
	 */
	ProcessingResult process(String filePath, FileEvent event, FileMonitorConfig config);

	/**
	 * 处理器配置
	 */
	default Map<String, Object> getConfig() {
		return Collections.emptyMap();
	}
}
