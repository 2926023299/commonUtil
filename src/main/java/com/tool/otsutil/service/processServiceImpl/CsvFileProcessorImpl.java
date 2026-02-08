package com.tool.otsutil.service.processServiceImpl;

import com.tool.otsutil.config.FileMonitorConfig;
import com.tool.otsutil.model.fileProcess.ProcessingResult;
import com.tool.otsutil.service.FileProcessor;
import com.tool.otsutil.model.fileProcess.FileEvent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV文件处理器
 */
@Component
@Slf4j
public class CsvFileProcessorImpl implements FileProcessor {

	@Override
	public String getName() {
		return "csv-processor";
	}

	@Override
	public boolean supports(String filePath, boolean isStable) {
		return filePath.toLowerCase().endsWith(".csv") &&
				isStable;
	}

	@Override
	public ProcessingResult process(String filePath, FileEvent event, FileMonitorConfig config) {
		try {
			log.info("处理CSV文件: {}", filePath);

			// 读取CSV文件
			List<String[]> csvData = readCsvFile(filePath);

			// 处理业务逻辑
			processCsvData(csvData, filePath);

			// 处理成功后移动文件到backup目录
			moveProcessedFile(filePath, true, config);
			return ProcessingResult.success("CSV文件处理成功", csvData.size());

		} catch (Exception e) {
			log.error("CSV文件处理失败: {}", filePath, e);
			// 处理失败后移动文件到error目录
			moveProcessedFile(filePath, false, config);
			return ProcessingResult.failure("CSV文件处理失败", e);
		}
	}

	private List<String[]> readCsvFile(String filePath) throws IOException {
		List<String[]> data = new ArrayList<>();
		Path path = Paths.get(filePath);

		try (BufferedReader reader = Files.newBufferedReader(path)) {
			String line;
			while ((line = reader.readLine()) != null) {
				String[] fields = line.split(",");
				data.add(fields);
			}
		}

		return data;
	}

	private void processCsvData(List<String[]> data, String filePath) {
		// 实现具体的CSV处理逻辑
		log.info("处理CSV数据，行数: {}", data.size());
		// 例如：数据验证、转换、存储等
	}
	
	/**
	 * 移动处理后的文件到backup或error文件夹
	 */
	private void moveProcessedFile(String filePath, boolean isSuccess, FileMonitorConfig config) {
		try {
			Path originalFile = Paths.get(filePath);
			Path monitorDir = Paths.get(config.getDirectory());
			
			// 创建backup和error文件夹
			Path backupDir = monitorDir.resolve("backup");
			Path errorDir = monitorDir.resolve("error");
			
			if (!Files.exists(backupDir)) {
				Files.createDirectories(backupDir);
				log.info("创建备份文件夹: {}", backupDir);
			}
			
			if (!Files.exists(errorDir)) {
				Files.createDirectories(errorDir);
				log.info("创建错误文件夹: {}", errorDir);
			}
			
			// 确定目标目录
			Path targetDir = isSuccess ? backupDir : errorDir;
			
			// 构建目标路径
			Path targetFile = targetDir.resolve(originalFile.getFileName());
			
			// 移动文件
			Files.move(originalFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
			log.info("文件已移动到: {}", targetFile);
			
		} catch (Exception e) {
			log.error("移动文件失败: {}", filePath, e);
		}
	}
}