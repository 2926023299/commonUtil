package com.tool.otsutil.util;

import com.tool.otsutil.config.FileMonitorConfig;
import com.tool.otsutil.model.fileProcess.FileEvent;
import com.tool.otsutil.model.fileProcess.FileEventType;
import com.tool.otsutil.model.fileProcess.ProcessingOutcome;
import com.tool.otsutil.model.fileProcess.ProcessingResult;
import com.tool.otsutil.service.FileProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.ClosedWatchServiceException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 通用文件监控服务
 */
@Slf4j
public class UniversalFileMonitorService {

    private static final String BACKUP_DIR = "backup";
    private static final String ERROR_DIR = "error";
    private static final String IGNORED_DIR = "ignored";

    private final Map<String, FileMonitorConfig> monitorConfigs = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> monitorTasks = new ConcurrentHashMap<>();
    private final Map<String, FileProcessor> fileProcessors = new ConcurrentHashMap<>();
    private final Map<String, WatchService> watchServices = new ConcurrentHashMap<>();
    private final Map<String, Semaphore> monitorSemaphores = new ConcurrentHashMap<>();

    private final ApplicationEventPublisher eventPublisher;
    private final FileProcessingStatusService statusService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private final ExecutorService processorExecutor = Executors.newCachedThreadPool();

    public UniversalFileMonitorService(ApplicationEventPublisher eventPublisher,
                                       FileProcessingStatusService statusService) {
        this.eventPublisher = eventPublisher;
        this.statusService = statusService;
        log.info("UniversalFileMonitorService 初始化完成");
    }

    public void registerProcessor(FileProcessor processor) {
        fileProcessors.put(processor.getName(), processor);
        log.info("注册文件处理器: {}", processor.getName());
    }

    public boolean startMonitoring(FileMonitorConfig config) {
        try {
            if (monitorConfigs.containsKey(config.getId())) {
                log.warn("监控配置已存在: {}", config.getId());
                return false;
            }

            Path directory = Paths.get(config.getDirectory());
            if (!Files.exists(directory)) {
                log.warn("监控目录不存在，创建目录: {}", config.getDirectory());
                Files.createDirectories(directory);
            }

            monitorConfigs.put(config.getId(), config);
            monitorSemaphores.put(config.getId(), new Semaphore(resolveMaxConcurrent(config)));

            ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                    new Runnable() {
                        @Override
                        public void run() {
                            scanDirectory(config);
                        }
                    },
                    0, config.getCheckInterval(), TimeUnit.SECONDS
            );

            monitorTasks.put(config.getId(), future);
            log.info("启动文件监控: {} -> {}", config.getId(), config.getDirectory());
            return true;
        } catch (Exception e) {
            log.error("启动文件监控失败: {}", config.getId(), e);
            return false;
        }
    }

    public boolean stopMonitoring(String monitorId) {
        try {
            ScheduledFuture<?> task = monitorTasks.remove(monitorId);
            if (task != null) {
                task.cancel(false);
            }

            monitorConfigs.remove(monitorId);
            monitorSemaphores.remove(monitorId);
            stopFileSystemWatch(monitorId);

            log.info("停止文件监控: {}", monitorId);
            return true;
        } catch (Exception e) {
            log.error("停止文件监控失败: {}", monitorId, e);
            return false;
        }
    }

    private void scanDirectory(FileMonitorConfig config) {
        try {
            Path directory = Paths.get(config.getDirectory());

            if (!Files.exists(directory) || !Files.isDirectory(directory)) {
                log.warn("监控目录不存在或不是目录: {}", config.getDirectory());
                return;
            }

            List<Path> files = findMatchingFiles(directory, config);
            for (Path file : files) {
                handleFileDetection(file, config);
            }

            log.debug("扫描目录完成: {}, 发现 {} 个文件", config.getDirectory(), files.size());
        } catch (Exception e) {
            log.error("扫描目录异常: {}", config.getDirectory(), e);
        }
    }

    private List<Path> findMatchingFiles(Path directory, FileMonitorConfig config) throws IOException {
        List<Path> matchingFiles = new ArrayList<>();
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + config.getFilePattern());

        try (Stream<Path> stream = Files.walk(directory, config.isRecursive() ? Integer.MAX_VALUE : 1)) {
            stream.filter(path -> isCandidateFile(path, directory, matcher))
                    .forEach(matchingFiles::add);
        }

        return matchingFiles;
    }

    private boolean isCandidateFile(Path path, Path rootDirectory, PathMatcher matcher) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        if (isHiddenFile(path)) {
            return false;
        }
        if (isManagedDirectoryFile(path, rootDirectory)) {
            return false;
        }
        return matcher.matches(path.getFileName());
    }

    private boolean isManagedDirectoryFile(Path path, Path rootDirectory) {
        Path relativePath = rootDirectory.relativize(path);
        return relativePath.startsWith(BACKUP_DIR)
                || relativePath.startsWith(ERROR_DIR)
                || relativePath.startsWith(IGNORED_DIR);
    }

    private void handleFileDetection(Path file, FileMonitorConfig config) {
        if (!statusService.shouldProcess(file)) {
            return;
        }

        if (isFileStable(file, config)) {
            triggerFileProcessing(file, config, FileEventType.UPLOAD_COMPLETED);
        } else if (!statusService.isMonitoring(file.toString())) {
            statusService.startMonitoring(file.toString());
            triggerFileEvent(file, config, FileEventType.UPLOAD_STARTED);
            startStabilityCheck(file, config);
        }
    }

    private boolean isFileStable(Path file, FileMonitorConfig config) {
        try {
            FileStabilityInfo stabilityInfo = statusService.getStabilityInfo(file.toString());
            long currentSize = Files.size(file);
            long currentTime = System.currentTimeMillis();

            if (stabilityInfo == null) {
                stabilityInfo = new FileStabilityInfo();
                stabilityInfo.setFilePath(file.toString());
                stabilityInfo.setLastSize(currentSize);
                stabilityInfo.setLastCheckTime(currentTime);
                stabilityInfo.setLastSizeChangeTime(currentTime);
                stabilityInfo.setStable(false);
                statusService.recordStabilityInfo(stabilityInfo);
                return false;
            }

            if (currentSize == stabilityInfo.getLastSize()) {
                long stableDuration = currentTime - stabilityInfo.getLastSizeChangeTime();
                stabilityInfo.setLastCheckTime(currentTime);
                if (stableDuration >= config.getStabilityThreshold()) {
                    stabilityInfo.setStable(true);
                    statusService.recordStabilityInfo(stabilityInfo);
                    return true;
                }
            } else {
                stabilityInfo.setLastSize(currentSize);
                stabilityInfo.setLastCheckTime(currentTime);
                stabilityInfo.setLastSizeChangeTime(currentTime);
                stabilityInfo.setStable(false);
                statusService.recordStabilityInfo(stabilityInfo);
            }

            return false;
        } catch (IOException e) {
            log.warn("检查文件稳定性失败: {}", file, e);
            return false;
        }
    }

    private void startStabilityCheck(final Path file, final FileMonitorConfig config) {
        scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                if (isFileStable(file, config)) {
                    triggerFileProcessing(file, config, FileEventType.UPLOAD_COMPLETED);
                } else if (statusService.isMonitoring(file.toString())) {
                    startStabilityCheck(file, config);
                }
            }
        }, config.getStabilityThreshold() / 1000, TimeUnit.SECONDS);
    }

    private void triggerFileProcessing(Path file, FileMonitorConfig config, FileEventType eventType) {
        String filePath = file.toString();
        FileEvent event = createFileEvent(file, config, eventType);

        FileStabilityInfo stabilityInfo = statusService.getStabilityInfo(filePath);
        boolean isStable = stabilityInfo != null && stabilityInfo.isStable();
        List<FileProcessor> processors = findProcessorsForFile(filePath, isStable);

        if (processors.isEmpty()) {
            completeWithoutProcessor(filePath, config,
                    ProcessingResult.ignored("未找到匹配的文件处理器"));
            return;
        }

        if (processors.size() > 1) {
            completeWithoutProcessor(filePath, config,
                    ProcessingResult.failure("命中多个文件处理器，视为配置冲突"));
            return;
        }

        Semaphore semaphore = monitorSemaphores.get(config.getId());
        if (semaphore != null && !semaphore.tryAcquire()) {
            log.debug("监控 {} 已达到最大并发限制，等待下次扫描重试: {}", config.getId(), filePath);
            return;
        }

        if (!statusService.startProcessing(filePath)) {
            releaseSemaphore(semaphore);
            return;
        }

        try {
            processorExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        processFileWithProcessor(filePath, event, processors.get(0), config);
                    } finally {
                        releaseSemaphore(semaphore);
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            releaseSemaphore(semaphore);
            statusService.forceReprocess(filePath);
            log.error("提交文件处理任务失败: {}", filePath, e);
        }
    }

    private void completeWithoutProcessor(String filePath, FileMonitorConfig config, ProcessingResult finalResult) {
        if (!statusService.startProcessing(filePath)) {
            return;
        }

        List<ProcessingResult> results = new ArrayList<>();
        results.add(finalResult);
        statusService.completeProcessing(filePath, results, finalResult);
        archiveFile(filePath, config, finalResult);
        triggerTerminalEvent(Paths.get(filePath), config, finalResult.getOutcome());
    }

    private void processFileWithProcessor(String filePath, FileEvent event,
                                          FileProcessor processor, FileMonitorConfig config) {
        triggerFileEvent(Paths.get(filePath), config, FileEventType.PROCESS_STARTED);

        List<ProcessingResult> results = new ArrayList<>();
        try {
            log.info("使用处理器 {} 处理文件: {}", processor.getName(), filePath);
            ProcessingResult result = processor.process(filePath, event, config);
            results.add(result);
        } catch (Exception e) {
            log.error("处理器 {} 执行异常: {}", processor.getName(), filePath, e);
            results.add(ProcessingResult.failure("处理器执行异常", e));
        }

        ProcessingResult finalResult = summarizeResults(results);
        statusService.completeProcessing(filePath, results, finalResult);
        archiveFile(filePath, config, finalResult);
        triggerTerminalEvent(Paths.get(filePath), config, finalResult.getOutcome());

        log.info("文件处理完成: {}, 最终结果: {}", filePath, finalResult.getOutcome());
    }

    private ProcessingResult summarizeResults(List<ProcessingResult> results) {
        if (results.isEmpty()) {
            return ProcessingResult.ignored("未执行任何处理器");
        }

        boolean hasFailure = false;
        boolean hasSuccess = false;
        for (ProcessingResult result : results) {
            if (result.isFailure()) {
                hasFailure = true;
            }
            if (result.isSuccess()) {
                hasSuccess = true;
            }
        }

        if (hasFailure) {
            return results.stream()
                    .filter(ProcessingResult::isFailure)
                    .findFirst()
                    .orElse(ProcessingResult.failure("处理失败"));
        }
        if (hasSuccess) {
            return results.stream()
                    .filter(ProcessingResult::isSuccess)
                    .findFirst()
                    .orElse(ProcessingResult.success("处理成功"));
        }
        return results.get(0);
    }

    private void archiveFile(String filePath, FileMonitorConfig config, ProcessingResult finalResult) {
        Path sourceFile = Paths.get(filePath);
        if (!Files.exists(sourceFile)) {
            log.warn("归档时文件不存在，跳过移动: {}", filePath);
            return;
        }

        Path monitorDir = Paths.get(config.getDirectory());
        Path targetDir = resolveArchiveDirectory(monitorDir, finalResult.getOutcome());
        Path targetFile = targetDir.resolve(sourceFile.getFileName());

        try {
            Files.createDirectories(targetDir);
            Files.move(sourceFile, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.info("文件已归档到 {}: {}", targetDir.getFileName(), targetFile);
        } catch (IOException e) {
            log.error("归档文件失败: {}", filePath, e);
        }
    }

    private Path resolveArchiveDirectory(Path monitorDir, ProcessingOutcome outcome) {
        if (outcome == ProcessingOutcome.FAILED) {
            return monitorDir.resolve(ERROR_DIR);
        }
        if (outcome == ProcessingOutcome.IGNORED) {
            return monitorDir.resolve(IGNORED_DIR);
        }
        return monitorDir.resolve(BACKUP_DIR);
    }

    private void triggerTerminalEvent(Path file, FileMonitorConfig config, ProcessingOutcome outcome) {
        if (outcome == ProcessingOutcome.FAILED) {
            triggerFileEvent(file, config, FileEventType.PROCESS_FAILED);
            return;
        }
        if (outcome == ProcessingOutcome.IGNORED) {
            triggerFileEvent(file, config, FileEventType.PROCESS_IGNORED);
            return;
        }
        triggerFileEvent(file, config, FileEventType.PROCESS_COMPLETED);
    }

    private List<FileProcessor> findProcessorsForFile(String filePath, boolean isStable) {
        return fileProcessors.values().stream()
                .filter(processor -> processor.supports(filePath, isStable))
                .collect(Collectors.toList());
    }

    private FileEvent createFileEvent(Path file, FileMonitorConfig config, FileEventType eventType) {
        FileEvent event = new FileEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setEventType(eventType);
        event.setFilePath(file.toString());
        event.setTimestamp(System.currentTimeMillis());
        event.setMonitorId(config.getId());

        try {
            event.setFileSize(Files.size(file));
        } catch (IOException e) {
            event.setFileSize(0);
        }

        return event;
    }

    private void triggerFileEvent(Path file, FileMonitorConfig config, FileEventType eventType) {
        eventPublisher.publishEvent(createFileEvent(file, config, eventType));
    }

    private void startFileSystemWatch(FileMonitorConfig config) {
        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            Path directory = Paths.get(config.getDirectory());

            directory.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);

            watchServices.put(config.getId(), watchService);
            startWatchThread(config.getId(), watchService);
        } catch (IOException e) {
            log.warn("启动文件系统监听失败: {}", config.getDirectory(), e);
        }
    }

    private void startWatchThread(String monitorId, WatchService watchService) {
        Thread watchThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        WatchKey key = watchService.take();
                        for (WatchEvent<?> watchEvent : key.pollEvents()) {
                            handleWatchEvent(monitorId, watchEvent);
                        }
                        key.reset();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("文件监听线程被中断: {}", monitorId);
                } catch (ClosedWatchServiceException e) {
                    log.info("文件监听服务已关闭: {}", monitorId);
                }
            }
        });

        watchThread.setName("FileWatch-" + monitorId);
        watchThread.setDaemon(true);
        watchThread.start();
    }

    private void handleWatchEvent(String monitorId, WatchEvent<?> watchEvent) {
        FileMonitorConfig config = monitorConfigs.get(monitorId);
        if (config == null) {
            return;
        }

        Path context = (Path) watchEvent.context();
        Path fullPath = Paths.get(config.getDirectory()).resolve(context);
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + config.getFilePattern());
        if (!matcher.matches(context)) {
            return;
        }

        if (watchEvent.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
            triggerFileEvent(fullPath, config, FileEventType.FILE_CREATED);
        } else if (watchEvent.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
            triggerFileEvent(fullPath, config, FileEventType.FILE_MODIFIED);
        } else if (watchEvent.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
            triggerFileEvent(fullPath, config, FileEventType.FILE_DELETED);
        }
    }

    private void stopFileSystemWatch(String monitorId) {
        try {
            WatchService watchService = watchServices.remove(monitorId);
            if (watchService != null) {
                watchService.close();
            }
        } catch (IOException e) {
            log.warn("停止文件系统监听失败: {}", monitorId, e);
        }
    }

    private boolean isHiddenFile(Path path) {
        try {
            return Files.isHidden(path) || path.getFileName().toString().startsWith(".");
        } catch (IOException e) {
            return false;
        }
    }

    private int resolveMaxConcurrent(FileMonitorConfig config) {
        return config.getMaxConcurrent() > 0 ? config.getMaxConcurrent() : 1;
    }

    private void releaseSemaphore(Semaphore semaphore) {
        if (semaphore != null) {
            semaphore.release();
        }
    }

    public List<FileMonitorConfig> getMonitorConfigs() {
        return new ArrayList<>(monitorConfigs.values());
    }

    public Map<String, Object> getMonitorStatus(String monitorId) {
        FileMonitorConfig config = monitorConfigs.get(monitorId);
        if (config == null) {
            return null;
        }

        Map<String, Object> status = new HashMap<>();
        status.put("config", config);
        status.put("active", monitorTasks.containsKey(monitorId));
        status.put("directoryExists", Files.exists(Paths.get(config.getDirectory())));
        status.put("recentResults", statusService.getFileStatuses(config.getDirectory()).stream()
                .limit(10)
                .map(this::toStatusSummary)
                .collect(Collectors.toList()));

        return status;
    }

    private Map<String, Object> toStatusSummary(FileProcessingStatus fileProcessingStatus) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("filePath", fileProcessingStatus.getFilePath());
        summary.put("processing", fileProcessingStatus.isProcessing());
        summary.put("startTime", fileProcessingStatus.getStartTime());
        summary.put("endTime", fileProcessingStatus.getEndTime());
        summary.put("finalResult", fileProcessingStatus.getFinalResult());
        summary.put("results", fileProcessingStatus.getResults());
        return summary;
    }

    @PreDestroy
    public void destroy() {
        for (String monitorId : new ArrayList<>(monitorConfigs.keySet())) {
            stopMonitoring(monitorId);
        }

        scheduler.shutdown();
        processorExecutor.shutdown();

        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            if (!processorExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                processorExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            processorExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
