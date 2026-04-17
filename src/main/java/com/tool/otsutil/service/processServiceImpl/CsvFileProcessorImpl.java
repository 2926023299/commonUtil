package com.tool.otsutil.service.processServiceImpl;

import com.tool.otsutil.config.FileMonitorConfig;
import com.tool.otsutil.model.fileProcess.FileEvent;
import com.tool.otsutil.model.fileProcess.ProcessingResult;
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
public class CsvFileProcessorImpl extends AbstractFileProcessor {

    @Override
    public String getName() {
        return "csv-processor";
    }

    @Override
    public boolean supports(String filePath, boolean isStable) {
        return filePath.toLowerCase().endsWith(".csv") && isStable;
    }

    @Override
    public ProcessingResult process(String filePath, FileEvent event, FileMonitorConfig config) {
        try {
            log.info("处理CSV文件: {}", filePath);

            List<String[]> csvData = readCsvFile(filePath);
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
                data.add(line.split(","));
            }
        }

        return data;
    }

    private void processCsvData(List<String[]> data, String filePath) {
        log.info("处理CSV数据，行数: {}", data.size());
    }
}
