package com.tool.otsutil.util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.tool.otsutil.model.fileProcess.ProcessingOutcome;
import com.tool.otsutil.model.fileProcess.ProcessingResult;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class FileProcessingStatusService {

    private final Map<String, FileProcessingStatus> processingStatus = new ConcurrentHashMap<>();
    private final Map<String, FileStabilityInfo> stabilityInfoMap = new ConcurrentHashMap<>();
    private final Cache<String, ProcessedFileRecord> processedFiles = CacheBuilder.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .build();

    /**
     * 检查文件是否应该被处理
     */
    public boolean shouldProcess(Path file) {
        String filePath = file.toString();
        if (isProcessing(filePath)) {
            return false;
        }

        ProcessedFileRecord processedFileRecord = processedFiles.getIfPresent(filePath);
        if (processedFileRecord != null) {
            try {
                long currentSize = Files.size(file);
                long lastModified = Files.getLastModifiedTime(file).toMillis();
                return processedFileRecord.getLastSize() != currentSize
                        || processedFileRecord.getLastModifiedTime() != lastModified;
            } catch (IOException e) {
                return false;
            }
        }

        FileStabilityInfo stabilityInfo = stabilityInfoMap.get(filePath);
        if (stabilityInfo != null && stabilityInfo.isStable()) {
            return true;
        }

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
    public boolean startProcessing(String filePath) {
        FileProcessingStatus newStatus = new FileProcessingStatus();
        newStatus.setFilePath(filePath);
        newStatus.setStartTime(System.currentTimeMillis());
        newStatus.setProcessing(true);
        FileProcessingStatus existing = processingStatus.putIfAbsent(filePath, newStatus);
        if (existing == null) {
            return true;
        }
        if (existing.isProcessing()) {
            return false;
        }

        existing.setProcessing(true);
        existing.setStartTime(System.currentTimeMillis());
        existing.setEndTime(null);
        existing.setResults(null);
        existing.setFinalResult(null);
        return true;
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
    public void completeProcessing(String filePath, List<ProcessingResult> results, ProcessingResult finalResult) {
        FileProcessingStatus status = processingStatus.get(filePath);
        if (status != null) {
            status.setProcessing(false);
            status.setEndTime(System.currentTimeMillis());
            status.setResults(results);
            status.setFinalResult(finalResult);
        }

        ProcessedFileRecord processedFileRecord = buildProcessedFileRecord(filePath, finalResult);
        if (processedFileRecord != null) {
            processedFiles.put(filePath, processedFileRecord);
        } else {
            processedFiles.invalidate(filePath);
        }

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

    public List<FileProcessingStatus> getFileStatuses(String directory) {
        return processingStatus.values().stream()
                .filter(status -> status.getFilePath() != null && status.getFilePath().startsWith(directory))
                .sorted(Comparator.comparingLong(FileProcessingStatus::getStartTime).reversed())
                .collect(Collectors.toList());
    }

    private ProcessedFileRecord buildProcessedFileRecord(String filePath, ProcessingResult finalResult) {
        Path file = Paths.get(filePath);
        if (!Files.exists(file)) {
            return null;
        }

        try {
            ProcessedFileRecord record = new ProcessedFileRecord();
            record.setLastSize(Files.size(file));
            record.setLastModifiedTime(Files.getLastModifiedTime(file).toMillis());
            if (finalResult != null) {
                record.setOutcome(finalResult.getOutcome());
            }
            return record;
        } catch (IOException e) {
            return null;
        }
    }
}

@Data
class FileProcessingStatus {
    private String filePath;
    private boolean processing;
    private long startTime;
    private Long endTime;
    private List<ProcessingResult> results;
    private ProcessingResult finalResult;
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

@Data
class ProcessedFileRecord {
    private long lastSize;
    private long lastModifiedTime;
    private ProcessingOutcome outcome;
}
