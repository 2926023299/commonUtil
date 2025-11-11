package com.tool.otsutil.util;

import com.tool.otsutil.config.FileMonitorConfig;
import com.tool.otsutil.model.fileProcess.ProcessingResult;
import com.tool.otsutil.model.fileProcess.FileEvent;
import com.tool.otsutil.model.fileProcess.FileEventType;
import com.tool.otsutil.service.FileProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 通用文件监控服务
 */
@Slf4j
public class UniversalFileMonitorService {

	// 存储所有监控配置
	private final Map<String, FileMonitorConfig> monitorConfigs = new ConcurrentHashMap<>();

	// 存储监控任务
	private final Map<String, ScheduledFuture<?>> monitorTasks = new ConcurrentHashMap<>();

	// 文件处理器注册表
	private final Map<String, FileProcessor> fileProcessors = new ConcurrentHashMap<>();

	// 事件发布器
	private final ApplicationEventPublisher eventPublisher;

	// 处理状态服务
	private final FileProcessingStatusService statusService;

	// 线程池
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
	private final ExecutorService processorExecutor = Executors.newCachedThreadPool();

	// 文件观察服务（用于实时监控）
	private final Map<String, WatchService> watchServices = new ConcurrentHashMap<>();

	@Autowired
	public UniversalFileMonitorService(ApplicationEventPublisher eventPublisher,
									   FileProcessingStatusService statusService) {
		this.eventPublisher = eventPublisher;
		this.statusService = statusService;
		log.info("UniversalFileMonitorService 初始化完成");
	}

	/**
	 * 注册文件处理器
	 */
	public void registerProcessor(FileProcessor processor) {
		fileProcessors.put(processor.getName(), processor);
		log.info("注册文件处理器: {}", processor.getName());
	}

	/**
	 * 启动文件监控
	 */
	public boolean startMonitoring(FileMonitorConfig config) {
		try {
			if (monitorConfigs.containsKey(config.getId())) {
				log.warn("监控配置已存在: {}", config.getId());
				return false;
			}

			// 验证目录是否存在
			Path directory = Paths.get(config.getDirectory());
			if (!Files.exists(directory)) {
				log.warn("监控目录不存在，创建目录: {}", config.getDirectory());
				Files.createDirectories(directory);
			}

			monitorConfigs.put(config.getId(), config);

			// 启动监控任务
			ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
					() -> scanDirectory(config),
					0, config.getCheckInterval(), TimeUnit.SECONDS
			);

			monitorTasks.put(config.getId(), future);

			// 启动文件系统监听（可选），不用这个了
			//startFileSystemWatch(config);

			log.info("启动文件监控: {} -> {}", config.getId(), config.getDirectory());
			return true;

		} catch (Exception e) {
			log.error("启动文件监控失败: {}", config.getId(), e);
			return false;
		}
	}

	/**
	 * 停止文件监控
	 */
	public boolean stopMonitoring(String monitorId) {
		try {
			ScheduledFuture<?> task = monitorTasks.remove(monitorId);
			if (task != null) {
				task.cancel(false);
			}

			monitorConfigs.remove(monitorId);

			// 停止文件系统监听
			stopFileSystemWatch(monitorId);

			log.info("停止文件监控: {}", monitorId);
			return true;

		} catch (Exception e) {
			log.error("停止文件监控失败: {}", monitorId, e);
			return false;
		}
	}

	/**
	 * 扫描目录处理文件
	 */
	private void scanDirectory(FileMonitorConfig config) {
		try {
			Path directory = Paths.get(config.getDirectory());

			if (!Files.exists(directory) || !Files.isDirectory(directory)) {
				log.warn("监控目录不存在或不是目录: {}", config.getDirectory());
				return;
			}

			// 查找匹配的文件
			List<Path> files = findMatchingFiles(directory, config);

			for (Path file : files) {
				handleFileDetection(file, config);
			}

			log.debug("扫描目录完成: {}, 发现 {} 个文件", config.getDirectory(), files.size());

		} catch (Exception e) {
			log.error("扫描目录异常: {}", config.getDirectory(), e);
		}
	}

	/**
	 * 查找匹配的文件
	 */
	private List<Path> findMatchingFiles(Path directory, FileMonitorConfig config) throws IOException {
		List<Path> matchingFiles = new ArrayList<>();

		// 构建文件匹配器
		PathMatcher matcher = FileSystems.getDefault().getPathMatcher(
				"glob:" + config.getFilePattern()
		);

		// 搜索文件
		Files.walk(directory, config.isRecursive() ? Integer.MAX_VALUE : 1)
				.filter(path -> {
					if (!Files.isRegularFile(path)) {
						return false;
					}
					if (isHiddenFile(path)) {
						return false;
					}
					return matcher.matches(path.getFileName());
				})
				.forEach(matchingFiles::add);

		return matchingFiles;
	}

	/**
	 * 处理检测到的文件
	 */
	private void handleFileDetection(Path file, FileMonitorConfig config) {
		String filePath = file.toString();

		// 检查文件状态
		if (!statusService.shouldProcess(filePath)) {
			return;
		}

		// 检查文件是否稳定（上传完成）
		if (isFileStable(file, config)) {
			// 文件稳定，触发处理
			triggerFileProcessing(file, config, FileEventType.UPLOAD_COMPLETED);
		} else {
			// 文件不稳定，可能是正在上传
			if (!statusService.isMonitoring(filePath)) {
				statusService.startMonitoring(filePath);
				triggerFileEvent(file, config, FileEventType.UPLOAD_STARTED);

				// 启动稳定性检查任务
				startStabilityCheck(file, config);
			}
		}
	}

	/**
	 * 检查文件是否稳定
	 */
	private boolean isFileStable(Path file, FileMonitorConfig config) {
		try {
			FileStabilityInfo stabilityInfo = statusService.getStabilityInfo(file.toString());
			long currentSize = Files.size(file);
			long currentTime = System.currentTimeMillis();

			if (stabilityInfo == null) {
				// 第一次检查，记录初始状态
				stabilityInfo = new FileStabilityInfo();
				stabilityInfo.setFilePath(file.toString());
				stabilityInfo.setLastSize(currentSize);
				stabilityInfo.setLastCheckTime(currentTime);
				stabilityInfo.setStable(false);
				statusService.recordStabilityInfo(stabilityInfo);
				return false;
			}

			if (currentSize == stabilityInfo.getLastSize()) {
				// 文件大小未变化
				long stableDuration = currentTime - stabilityInfo.getLastSizeChangeTime();
				if (stableDuration >= config.getStabilityThreshold()) {
					stabilityInfo.setStable(true);
					statusService.recordStabilityInfo(stabilityInfo);
					return true;
				}
			} else {
				// 文件大小变化
				stabilityInfo.setLastSize(currentSize);
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

	/**
	 * 启动稳定性检查
	 */
	private void startStabilityCheck(Path file, FileMonitorConfig config) {
		scheduler.schedule(() -> {
			if (isFileStable(file, config)) {
				triggerFileProcessing(file, config, FileEventType.UPLOAD_COMPLETED);
			} else {
				// 继续检查，直到稳定或超时
				if (statusService.isMonitoring(file.toString())) {
					startStabilityCheck(file, config);
				}
			}
		}, config.getStabilityThreshold() / 1000, TimeUnit.SECONDS);
	}

	/**
	 * 触发文件处理
	 */
	private void triggerFileProcessing(Path file, FileMonitorConfig config, FileEventType eventType) {
		String filePath = file.toString();

		// 创建文件事件
		FileEvent event = createFileEvent(file, config, eventType);

		// 发布事件
		//eventPublisher.publishEvent(event);

		// 查找匹配的处理器
		List<FileProcessor> processors = findProcessorsForFile(filePath, statusService.getStabilityInfo(filePath).isStable());

		if (processors.isEmpty()) {
			log.warn("未找到匹配的文件处理器: {}", filePath);
			return;
		}

		// 异步处理文件
		processorExecutor.submit(() -> {
			processFileWithProcessors(filePath, event, processors, config);
		});
	}

	/**
	 * 使用处理器处理文件
	 */
	private void processFileWithProcessors(String filePath, FileEvent event,
										   List<FileProcessor> processors, FileMonitorConfig config) {
		// 标记处理开始
		statusService.startProcessing(filePath);
		triggerFileEvent(Paths.get(filePath), config, FileEventType.PROCESS_STARTED);

		List<ProcessingResult> results = new ArrayList<>();

		for (FileProcessor processor : processors) {
			try {
				log.info("使用处理器 {} 处理文件: {}", processor.getName(), filePath);

				ProcessingResult result = processor.process(filePath, event, config);
				results.add(result);

				if (result.isSuccess()) {
					log.info("处理器 {} 处理成功: {}", processor.getName(), filePath);
				} else {
					log.error("处理器 {} 处理失败: {} - {}",
							processor.getName(), filePath, result.getMessage());
					triggerFileEvent(Paths.get(filePath), config, FileEventType.PROCESS_FAILED);
				}

			} catch (Exception e) {
				log.error("处理器 {} 执行异常: {}", processor.getName(), filePath, e);
				results.add(ProcessingResult.failure("处理器执行异常", e));
				triggerFileEvent(Paths.get(filePath), config, FileEventType.PROCESS_FAILED);
			}
		}

		// 标记处理完成
		statusService.completeProcessing(filePath, results);
		triggerFileEvent(Paths.get(filePath), config, FileEventType.PROCESS_COMPLETED);

		log.info("文件处理完成: {}, 结果: {}", filePath,
				results.stream().filter(ProcessingResult::isSuccess).count() + "/" + results.size());
	}

	/**
	 * 查找匹配的处理器
	 */
	private List<FileProcessor> findProcessorsForFile(String filePath, boolean isStable) {
		return fileProcessors.values().stream()
				.filter(processor -> processor.supports(filePath, isStable))
				.collect(Collectors.toList());
	}

	/**
	 * 创建文件事件
	 */
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

	/**
	 * 触发文件事件
	 */
	private void triggerFileEvent(Path file, FileMonitorConfig config, FileEventType eventType) {
		FileEvent event = createFileEvent(file, config, eventType);
		eventPublisher.publishEvent(event);
	}

	/**
	 * 启动文件系统监听（实时监控）
	 */
	private void startFileSystemWatch(FileMonitorConfig config) {

		try {
			WatchService watchService = FileSystems.getDefault().newWatchService();
			Path directory = Paths.get(config.getDirectory());

			WatchKey key = directory.register(watchService,
					StandardWatchEventKinds.ENTRY_CREATE,
					StandardWatchEventKinds.ENTRY_MODIFY,
					StandardWatchEventKinds.ENTRY_DELETE);

			watchServices.put(config.getId(), watchService);

			// 启动监听线程
			startWatchThread(config.getId(), watchService);

		} catch (IOException e) {
			log.warn("启动文件系统监听失败: {}", config.getDirectory(), e);
		}
	}

	/**
	 * 启动监听线程
	 */
	private void startWatchThread(String monitorId, WatchService watchService) {
		Thread watchThread = new Thread(() -> {
			try {
				while (!Thread.currentThread().isInterrupted()) {
					log.info("触发事件...");
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
		});

		watchThread.setName("FileWatch-" + monitorId);
		watchThread.setDaemon(true);
		watchThread.start();
	}

	/**
	 * 处理文件系统事件,对创建文件、修改文件、删除文件时触发相应的事件，但是判断不了上传中的文件
	 */
	private void handleWatchEvent(String monitorId, WatchEvent<?> watchEvent) {
		FileMonitorConfig config = monitorConfigs.get(monitorId);
		if (config == null) return;

		Path context = (Path) watchEvent.context();
		Path fullPath = Paths.get(config.getDirectory()).resolve(context);

		// 检查文件是否匹配模式
		PathMatcher matcher = FileSystems.getDefault().getPathMatcher(
				"glob:" + config.getFilePattern()
		);

		if (!matcher.matches(context)) {
			return;
		}

		// 触发相应的事件
		if (watchEvent.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
			triggerFileEvent(fullPath, config, FileEventType.FILE_CREATED);
		} else if (watchEvent.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
			triggerFileEvent(fullPath, config, FileEventType.FILE_MODIFIED);
		} else if (watchEvent.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
			triggerFileEvent(fullPath, config, FileEventType.FILE_DELETED);
		}
	}

	/**
	 * 停止文件系统监听
	 */
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

	/**
	 * 获取所有监控配置
	 */
	public List<FileMonitorConfig> getMonitorConfigs() {
		return new ArrayList<>(monitorConfigs.values());
	}

	/**
	 * 获取监控状态
	 */
	public Map<String, Object> getMonitorStatus(String monitorId) {
		FileMonitorConfig config = monitorConfigs.get(monitorId);
		if (config == null) {
			return null;
		}

		Map<String, Object> status = new HashMap<>();
		status.put("config", config);
		status.put("active", monitorTasks.containsKey(monitorId));
		status.put("directoryExists", Files.exists(Paths.get(config.getDirectory())));

		return status;
	}

	@PreDestroy
	public void destroy() {
		// 停止所有监控任务
		monitorConfigs.keySet().forEach(this::stopMonitoring);

		// 关闭线程池
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