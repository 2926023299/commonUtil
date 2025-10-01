package com.tool.otsutil.util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.tool.otsutil.model.fileProcess.ProcessingResult;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class FileProcessingStatusService {

	private final Map<String, FileProcessingStatus> processingStatus = new ConcurrentHashMap<>();
	private final Map<String, FileStabilityInfo> stabilityInfoMap = new ConcurrentHashMap<>();
	private final Cache<String, Boolean> processedFiles = CacheBuilder.newBuilder()
			.expireAfterWrite(24, TimeUnit.HOURS)
			.build();

	/**
	 * 检查文件是否应该被处理
	 */
	public boolean shouldProcess(String filePath) {
		// 检查是否正在处理
		if (isProcessing(filePath)) {
			return false;
		}

		// 检查是否已经处理过
//		if (processedFiles.getIfPresent(filePath) != null) {
//			return false;
//		}

		// 检查是否在监控中但还未稳定
		FileStabilityInfo stabilityInfo = stabilityInfoMap.get(filePath);
		if (stabilityInfo != null && stabilityInfo.isStable()) {
			return true;
		}

		// 新文件或重新处理的文件
		return true;
	}

	/**
	 * 开始监控文件
	 */
	public void startMonitoring(String filePath) {
		FileStabilityInfo info = new FileStabilityInfo();
		info.setFilePath(filePath);
		info.setMonitoring(true);
		stabilityInfoMap.put(filePath, info);
	}

	/**
	 * 检查是否在监控中
	 */
	public boolean isMonitoring(String filePath) {
		FileStabilityInfo info = stabilityInfoMap.get(filePath);
		return info != null && info.isMonitoring();
	}

	/**
	 * 开始处理文件
	 */
	public void startProcessing(String filePath) {
		FileProcessingStatus status = new FileProcessingStatus();
		status.setFilePath(filePath);
		status.setStartTime(System.currentTimeMillis());
		status.setProcessing(true);
		processingStatus.put(filePath, status);
	}

	/**
	 * 检查是否正在处理
	 */
	public boolean isProcessing(String filePath) {
		FileProcessingStatus status = processingStatus.get(filePath);
		return status != null && status.isProcessing();
	}

	/**
	 * 完成文件处理
	 */
	public void completeProcessing(String filePath, List<ProcessingResult> results) {
		FileProcessingStatus status = processingStatus.get(filePath);
		if (status != null) {
			status.setProcessing(false);
			status.setEndTime(System.currentTimeMillis());
			status.setResults(results);
		}

		// 标记为已处理
		processedFiles.put(filePath, true);

		// 清理稳定性信息
		stabilityInfoMap.remove(filePath);
	}

	/**
	 * 记录稳定性信息
	 */
	public void recordStabilityInfo(FileStabilityInfo info) {
		stabilityInfoMap.put(info.getFilePath(), info);
	}

	/**
	 * 获取稳定性信息
	 */
	public FileStabilityInfo getStabilityInfo(String filePath) {
		return stabilityInfoMap.get(filePath);
	}

	/**
	 * 强制重新处理文件
	 */
	public boolean forceReprocess(String filePath) {
		processedFiles.invalidate(filePath);
		processingStatus.remove(filePath);
		stabilityInfoMap.remove(filePath);
		return true;
	}
}

@Data
class FileProcessingStatus {
	private String filePath;
	private boolean processing;
	private long startTime;
	private Long endTime;
	private List<ProcessingResult> results;
}

@Data
class FileStabilityInfo {
	private String filePath;
	private boolean monitoring;
	private boolean stable;
	private long lastSize;
	private long lastCheckTime;
	private long lastSizeChangeTime;
}