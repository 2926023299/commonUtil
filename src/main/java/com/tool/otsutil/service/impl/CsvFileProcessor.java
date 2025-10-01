package com.tool.otsutil.service.impl;

import com.tool.otsutil.config.FileMonitorConfig;
import com.tool.otsutil.model.fileProcess.ProcessingResult;
import com.tool.otsutil.model.fileProcess.FileEvent;
import com.tool.otsutil.model.fileProcess.FileEventType;
import com.tool.otsutil.service.FileProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV文件处理器
 */
@Component
@Slf4j
public class CsvFileProcessor implements FileProcessor {

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

			return ProcessingResult.success("CSV文件处理成功", csvData.size());

		} catch (Exception e) {
			log.error("CSV文件处理失败: {}", filePath, e);
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
}