package com.tool.otsutil.service.processServiceImpl;

import com.tool.otsutil.config.FileMonitorConfig;
import com.tool.otsutil.model.entity.BreakerEnergyData;
import com.tool.otsutil.model.fileProcess.FileEvent;
import com.tool.otsutil.model.fileProcess.ProcessingResult;
import com.tool.otsutil.service.brekerService.BreakerEnergyDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;

/**
 * 断路器能耗数据文件处理器
 */
@Component
@Slf4j
public class BreakerEnergyFileProcessorImpl extends AbstractFileProcessor {

    private static final SimpleDateFormat FILE_TIME_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");

    static {
        FILE_TIME_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+8"));
    }

    private final BreakerEnergyDataService breakerEnergyDataService;

    public BreakerEnergyFileProcessorImpl(BreakerEnergyDataService breakerEnergyDataService) {
        this.breakerEnergyDataService = breakerEnergyDataService;
    }

    @Override
    public String getName() {
        return "breaker-energy-processor";
    }

    @Override
    public boolean supports(String filePath, boolean isStable) {
        return filePath.toLowerCase().endsWith(".txt")
                && filePath.matches(".*\\d{5}_\\d{2}_\\d{14}_\\d{4}\\.txt")
                && isStable;
    }

    @Override
    public ProcessingResult process(String filePath, FileEvent event, FileMonitorConfig config) {
        try {
            log.info("处理断路器能耗数据文件: {}", filePath);

            int processedCount = parseFileAndSaveData(filePath);
            log.info("处理完成，共保存 {} 条数据到数据库", processedCount);

            return ProcessingResult.success("断路器能耗数据文件处理成功", processedCount);
        } catch (Exception e) {
            log.error("断路器能耗数据文件处理失败: {}", filePath, e);
            return ProcessingResult.failure("断路器能耗数据文件处理失败", e);
        }
    }

    private int parseFileAndSaveData(String filePath) throws IOException, ParseException {
        int count = 0;
        File file = new File(filePath);
        String fileName = file.getName();
        Integer cityCode = null;
        Date fileTime = null;
        ArrayList<BreakerEnergyData> dataList = new ArrayList<>();

        String[] fileNameParts = fileName.split("_");
        if (fileNameParts.length >= 3) {
            cityCode = Integer.parseInt(fileNameParts[0]);
            fileTime = FILE_TIME_FORMAT.parse(fileNameParts[2]);
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath), Charset.forName("UTF-8")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                if (line.startsWith("@") || !line.startsWith("#")) {
                    continue;
                }

                String[] dataParts = line.substring(1).trim().split("\\t");
                if (dataParts.length >= 4) {
                    try {
                        BreakerEnergyData data = new BreakerEnergyData();
                        data.setBreakerId(dataParts[0]);
                        data.setDataTime(dataParts[1]);
                        data.setDataType(Integer.parseInt(dataParts[2]));
                        data.setDataValue(parseDataValue(dataParts[3], filePath, line));
                        data.setCityCode(cityCode);
                        data.setFileTime(fileTime);
                        data.setCreateTime(new Date());
                        data.setUpdateTime(new Date());

                        dataList.add(data);
                        count++;

                        if (dataList.size() >= 1000) {
                            breakerEnergyDataService.saveBatch(dataList);
                            dataList.clear();
                        }
                    } catch (NumberFormatException e) {
                        log.warn("跳过无效数字格式: {}, 文件: {}, 行: {}", dataParts[2], filePath, line);
                    }
                }
            }

            if (!dataList.isEmpty()) {
                breakerEnergyDataService.saveBatch(dataList);
            }
        }

        return count;
    }

    private Double parseDataValue(String valueStr, String filePath, String line) {
        try {
            double value = Double.parseDouble(valueStr);
            if (Double.isFinite(value)) {
                return value;
            }
        } catch (NumberFormatException ignored) {
        }

        try {
            double value = Double.parseDouble(cleanScientificNotation(valueStr));
            if (Double.isFinite(value)) {
                return value;
            }
        } catch (NumberFormatException ignored) {
        }

        try {
            String lastTryValue = valueStr.replaceAll("[^0-9.E-]", "");
            if (lastTryValue.contains(".")) {
                int dotIndex = lastTryValue.indexOf(".");
                lastTryValue = lastTryValue.substring(0, dotIndex + 1)
                        + lastTryValue.substring(dotIndex + 1).replace(".", "");
            }
            if (lastTryValue.contains("E")) {
                int eIndex = lastTryValue.indexOf("E");
                lastTryValue = lastTryValue.substring(0, eIndex + 1)
                        + lastTryValue.substring(eIndex + 1).replaceAll("[Ee]", "");
            }
            if (lastTryValue.isEmpty() || ".".equals(lastTryValue) || "E".equals(lastTryValue) || "e".equals(lastTryValue)) {
                throw new NumberFormatException("empty value");
            }
            double value = Double.parseDouble(lastTryValue);
            if (Double.isFinite(value)) {
                return value;
            }
        } catch (NumberFormatException ignored) {
        }

        log.warn("跳过无效数值: {}, 文件: {}, 行: {}", valueStr, filePath, line);
        throw new NumberFormatException(valueStr);
    }

    private String cleanScientificNotation(String valueStr) {
        if (valueStr.contains("E") && valueStr.matches(".*E.*E.*")) {
            String[] parts = valueStr.split("E");
            if (parts.length >= 2) {
                for (int i = parts.length - 1; i >= 1; i--) {
                    String base = parts[i - 1].replaceAll("[^0-9.]", "");
                    String exponent = parts[i].replaceAll("[^0-9-]", "");
                    if (!base.isEmpty() && !exponent.isEmpty()) {
                        return base + "E" + exponent;
                    }
                }
            }
        }

        if (".E0".equals(valueStr)) {
            return "0.0";
        }

        return valueStr;
    }
}
