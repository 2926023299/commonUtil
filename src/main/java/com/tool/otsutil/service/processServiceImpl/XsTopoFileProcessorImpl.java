package com.tool.otsutil.service.processServiceImpl;

import com.tool.otsutil.config.FileMonitorConfig;
import com.tool.otsutil.mapper.TopoDeviceMapper;
import com.tool.otsutil.model.dto.DeviceTopoInfo;
import com.tool.otsutil.model.fileProcess.FileEvent;
import com.tool.otsutil.model.fileProcess.ProcessingResult;
import com.tool.otsutil.service.FileProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * XS拓扑文件处理器，用于处理modelmanage.log文件
 */
@Component
@Slf4j
public class XsTopoFileProcessorImpl implements FileProcessor {

    @Autowired
    private TopoDeviceMapper topoDeviceMapper;

    // Excel导出路径
    @Value("${excel.export-path}")
    private String excelExportPath;

    @Override
    public String getName() {
        return "xs-topo-processor";
    }

    @Override
    public boolean supports(String filePath, boolean isStable) {
        return filePath.toLowerCase().endsWith(".log") && isStable;
    }

    @Override
    public ProcessingResult process(String filePath, FileEvent event, FileMonitorConfig config) {
        try {
            log.info("处理XS拓扑文件: {}", filePath);

            // 1. 从文件中提取设备ID
            List<String> deviceIds = extractDeviceIds(filePath, config);
            log.info("提取到设备ID数量: {}", deviceIds.size());

            if (deviceIds.isEmpty()) {
                moveProcessedFile(filePath, true, config);
                return ProcessingResult.success("XS拓扑文件处理成功，未提取到设备ID", 0);
            }

            // 2. 从数据库查询设备信息和馈线名称
            log.info("开始从数据库查询设备信息，设备ID数量: {}", deviceIds.size());

            // 分批处理设备ID，每批1000个
            int batchSize = 1000;
            List<Map<String, Object>> deviceInfoList = new ArrayList<>();

            for (int i = 0; i < deviceIds.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, deviceIds.size());
                List<String> batchIds = deviceIds.subList(i, endIndex);
                log.info("处理批次: {}-{}/{}", i + 1, endIndex, deviceIds.size());

                List<Map<String, Object>> batchResult = topoDeviceMapper.selectDeviceInfoByIds(batchIds);
                deviceInfoList.addAll(batchResult);
            }

            log.info("查询到设备信息数量: {}", deviceInfoList.size());

            // 3. 将查询结果转换为Map，方便后续处理
            log.info("开始将查询结果转换为Map");
            Map<String, Map<String, Object>> deviceInfoMap = new HashMap<>();
            for (Map<String, Object> deviceInfo : deviceInfoList) {
                String id = String.valueOf(deviceInfo.get("id"));
                deviceInfoMap.put(id, deviceInfo);
            }
            log.info("查询结果转换为Map完成，Map大小: {}", deviceInfoMap.size());

            // 4. 生成Excel文件
            log.info("开始生成Excel文件");
            String excelFilePath = generateExcel(deviceIds, deviceInfoMap, filePath);
            log.info("Excel文件生成成功: {}", excelFilePath);

            // 处理成功后移动文件到backup目录
            moveProcessedFile(filePath, true, config);
            return ProcessingResult.success("XS拓扑文件处理成功，生成Excel文件", deviceIds.size());
        } catch (Exception e) {
            log.error("XS拓扑文件处理失败: {}", filePath, e);
            // 处理失败后移动文件到error目录
            moveProcessedFile(filePath, false, config);
            return ProcessingResult.failure("XS拓扑文件处理失败", e);
        }
    }

    /**
     * 从modelmanage.log文件中提取设备ID
     */
    private List<String> extractDeviceIds(String filePath, FileMonitorConfig config) throws IOException {
        List<String> deviceIds = new ArrayList<>();

        // 使用UTF-8编码读取文件
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 查找包含"equip:"的行，格式为："2025-12-08 14:47:00 equip:5629499677686535"
                if (line.contains("equip:")) {
                    // 提取设备ID，格式为"equip:5629499677686535"
                    int startIndex = line.indexOf("equip:");
                    if (startIndex != -1) {
                        String deviceId = line.substring(startIndex + "equip:".length()).trim();
                        // 验证设备ID格式（16位数字）
                        if (deviceId.matches("\\d{16}")) {
                            deviceIds.add(deviceId);
                        }
                    }
                }
            }
        }

        log.info("从modelmanage.log提取到设备ID数量: {}", deviceIds.size());
        return deviceIds;
    }

    /**
     * 生成Excel文件
     */
    private String generateExcel(List<String> deviceIds, Map<String, Map<String, Object>> deviceInfoMap,
                                String filePath) throws IOException {
        // 1. 准备Excel数据
        List<DeviceTopoInfo> excelData = new ArrayList<>();

        // 按照原始设备ID顺序写入数据
        for (String deviceId : deviceIds) {
            DeviceTopoInfo deviceTopoInfo = new DeviceTopoInfo();
            deviceTopoInfo.setId(deviceId);

            Map<String, Object> deviceInfo = deviceInfoMap.get(deviceId);
            if (deviceInfo != null) {
                deviceTopoInfo.setDeviceName((String) deviceInfo.get("device_name"));
                deviceTopoInfo.setFeederName((String) deviceInfo.get("feeder_name"));
            }

            excelData.add(deviceTopoInfo);
        }

        // 2. 生成文件名
        String fileName = "XS设备拓扑信息_" + System.currentTimeMillis() + ".xlsx";
        String fullPath = excelExportPath + java.io.File.separator + fileName;

        // 3. 创建目录（如果不存在）
        java.io.File directory = new java.io.File(excelExportPath);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        // 4. 使用EasyExcel生成Excel文件
        com.alibaba.excel.EasyExcel.write(fullPath, DeviceTopoInfo.class)
                .sheet("XS设备拓扑信息")
                .doWrite(excelData);

        return fullPath;
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