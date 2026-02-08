package com.tool.otsutil.service.processServiceImpl;

import com.tool.otsutil.config.FileMonitorConfig;
import com.tool.otsutil.model.entity.BreakerEnergyData;
import com.tool.otsutil.model.fileProcess.FileEvent;
import com.tool.otsutil.model.fileProcess.ProcessingResult;
import com.tool.otsutil.service.FileProcessor;
import com.tool.otsutil.service.brekerService.BreakerEnergyDataService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
public class BreakerEnergyFileProcessorImpl implements FileProcessor {

    @Autowired
    private BreakerEnergyDataService breakerEnergyDataService;

    private static final SimpleDateFormat FILE_TIME_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");
    static {
        // 显式设置时区为GMT+8（北京时间），避免时区转换导致的时间偏移问题
        FILE_TIME_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT+8"));
    }


    @Override
    public String getName() {
        return "breaker-energy-processor";
    }

    @Override
    public boolean supports(String filePath, boolean isStable) {
        // 支持处理.txt文件，并且文件名符合格式：35401_99_20251209021503_0000.txt
        return filePath.toLowerCase().endsWith(".txt") && filePath.matches(".*\\d{5}_\\d{2}_\\d{14}_\\d{4}\\.txt") && isStable;
    }

    @Override
    public ProcessingResult process(String filePath, FileEvent event, FileMonitorConfig config) {
        try {
            log.info("处理断路器能耗数据文件: {}", filePath);

            // 1. 解析文件并提取数据
            int processedCount = parseFileAndSaveData(filePath);
            log.info("处理完成，共保存 {} 条数据到数据库", processedCount);

            // 处理成功后移动文件到backup目录
            moveProcessedFile(filePath, true, config);
            return ProcessingResult.success("断路器能耗数据文件处理成功", processedCount);
        } catch (Exception e) {
            log.error("断路器能耗数据文件处理失败: {}", filePath, e);
            // 处理失败后移动文件到error目录
            moveProcessedFile(filePath, false, config);
            return ProcessingResult.failure("断路器能耗数据文件处理失败", e);
        }
    }

    /**
     * 解析文件并保存数据到数据库
     */
    private int parseFileAndSaveData(String filePath) throws IOException, ParseException {
        int count = 0;
        File file = new File(filePath);
        String fileName = file.getName();
        Integer cityCode = null;
        Date fileTime = null;
        ArrayList<BreakerEnergyData> dataList = new ArrayList<>();

        // 解析文件名获取地市编码和文件时间
        String[] fileNameParts = fileName.split("_");
        if (fileNameParts.length >= 3) {
            // 获取地市编码（如35401）
            cityCode = Integer.parseInt(fileNameParts[0]);
            // 获取文件时间（如20251209021503）
            String fileTimeStr = fileNameParts[2];
            fileTime = FILE_TIME_FORMAT.parse(fileTimeStr);
        }

        // 使用UTF-8编码读取文件
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath), Charset.forName("UTF-8")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // 跳过表头和注释行
                if (line.startsWith("@") || !line.startsWith("#")) {
                    continue;
                }

                // 解析数据行
                String[] dataParts = line.substring(1).trim().split("\\t");
                if (dataParts.length >= 4) {
                    try {
                        BreakerEnergyData data = new BreakerEnergyData();
                        data.setBreakerId(dataParts[0]);
                        
                        // 直接使用原始时间字符串，不进行时间校验
                        String timeStr = dataParts[1];
                        data.setDataTime(timeStr);
                        
                        data.setDataType(Integer.parseInt(dataParts[2]));
                        
                        // 增强数据值解析
                        String valueStr = dataParts[3];
                        try {
                            // 直接尝试解析，包括科学计数法格式
                            double value = Double.parseDouble(valueStr);
                            // 检查是否为有效数值（不是无穷大或NaN）
                            if (Double.isFinite(value)) {
                                data.setDataValue(value);
                            } else {
                                log.warn("跳过无效数值: {}, 文件: {}, 行: {}", valueStr, filePath, line);
                                continue;
                            }
                        } catch (NumberFormatException e) {
                            // 尝试清理格式，然后再次解析
                            try {
                                String cleanedValue = cleanScientificNotation(valueStr);
                                double value = Double.parseDouble(cleanedValue);
                                if (Double.isFinite(value)) {
                                    data.setDataValue(value);
                                } else {
                                    log.warn("跳过无效数值: {}, 文件: {}, 行: {}", valueStr, filePath, line);
                                    continue;
                                }
                            } catch (NumberFormatException e2) {
                                // 最后尝试，移除所有非数字字符（保留数字、小数点、E、-）
                                try {
                                    String lastTryValue = valueStr.replaceAll("[^0-9.E-]", "");
                                    // 处理可能出现的多个小数点或E的情况
                                    if (lastTryValue.contains(".")) {
                                        // 只保留第一个小数点
                                        int dotIndex = lastTryValue.indexOf(".");
                                        lastTryValue = lastTryValue.substring(0, dotIndex + 1) + 
                                                    lastTryValue.substring(dotIndex + 1).replace(".", "");
                                    }
                                    if (lastTryValue.contains("E")) {
                                        // 只保留第一个E
                                        int eIndex = lastTryValue.indexOf("E");
                                        lastTryValue = lastTryValue.substring(0, eIndex + 1) + 
                                                    lastTryValue.substring(eIndex + 1).replaceAll("[Ee]", "");
                                    }
                                    // 处理空字符串情况
                                    if (lastTryValue.isEmpty() || lastTryValue.equals(".") || lastTryValue.equals("E") || lastTryValue.equals("e")) {
                                        log.warn("跳过无效数值: {}, 文件: {}, 行: {}", valueStr, filePath, line);
                                        continue;
                                    }
                                    double value = Double.parseDouble(lastTryValue);
                                    if (Double.isFinite(value)) {
                                        data.setDataValue(value);
                                    } else {
                                        log.warn("跳过无效数值: {}, 文件: {}, 行: {}", valueStr, filePath, line);
                                        continue;
                                    }
                                } catch (NumberFormatException e3) {
                                    log.warn("跳过无效数字格式: {}, 文件: {}, 行: {}", valueStr, filePath, line);
                                    continue;
                                }
                            }
                        }
                        
                        data.setCityCode(cityCode);
                        data.setFileTime(fileTime);
                        data.setCreateTime(new Date());
                        data.setUpdateTime(new Date());
                        
                        dataList.add(data);
                        count++;
                        
                        // 每1000条数据批量保存一次
                        if (dataList.size() >= 1000) {
                            breakerEnergyDataService.saveBatch(dataList);
                            dataList.clear();
                        }
                    } catch (NumberFormatException e) {
                        log.warn("跳过无效数字格式: {}, 文件: {}, 行: {}", dataParts[2], filePath, line);
                        continue;
                    }
                }
            }
            
            // 保存剩余数据
            if (!dataList.isEmpty()) {
                breakerEnergyDataService.saveBatch(dataList);
            }
        }

        return count;
    }
    

    
    /**
     * 清理科学计数法格式
     */
    private String cleanScientificNotation(String valueStr) {
        // 处理类似 "1155.E1155E.1155E22E22" 的格式
        if (valueStr.contains("E") && valueStr.matches(".*E.*E.*")) {
            // 尝试提取最后一个有效的科学计数法部分
            String[] parts = valueStr.split("E");
            if (parts.length >= 2) {
                try {
                    // 尝试找到最后一个有效的数字和指数
                    for (int i = parts.length - 1; i >= 1; i--) {
                        String base = parts[i-1];
                        String exponent = parts[i];
                        
                        // 清理base部分
                        base = base.replaceAll("[^0-9.]", "");
                        if (base.isEmpty()) continue;
                        
                        // 清理exponent部分
                        exponent = exponent.replaceAll("[^0-9-]", "");
                        if (exponent.isEmpty()) continue;
                        
                        return base + "E" + exponent;
                    }
                } catch (Exception e) {
                    // 如果清理失败，返回原始值
                }
            }
        }
        
        // 处理 ".E0" 这样的格式
        if (valueStr.equals(".E0")) {
            return "0.0";
        }
        
        return valueStr;
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