package com.tool.otsutil.service.processServiceImpl;

import com.alibaba.excel.EasyExcel;
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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 拓扑文件处理器
 */
@Component
@Slf4j
public class TopoFileProcessorImpl implements FileProcessor {

    @Autowired
    private TopoDeviceMapper topoDeviceMapper;

    // Excel导出路径
    @Value("${excel.export-path}")
    private String excelExportPath;

    @Override
    public String getName() {
        return "topo-processor";
    }

    @Override
    public boolean supports(String filePath, boolean isStable) {
        return filePath.toLowerCase().endsWith(".inf") && isStable;
    }

    @Override
    public ProcessingResult process(String filePath, FileEvent event, FileMonitorConfig config) {
        try {
            log.info("处理拓扑文件: {}", filePath);

            // 1. 从文件中提取设备ID
            List<String> deviceIds = extractDeviceIds(filePath, config);
            log.info("提取到设备ID数量: {}", deviceIds.size());

            if (deviceIds.isEmpty()) {
                moveProcessedFile(filePath, true, config);
                return ProcessingResult.success("拓扑文件处理成功，未提取到设备ID", 0);
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
                Object idObj = deviceInfo.get("id");
                String id;
                if (idObj instanceof BigDecimal) {
                    id = ((BigDecimal) idObj).toString();
                } else {
                    id = String.valueOf(idObj);
                }
                deviceInfoMap.put(id, deviceInfo);
            }
            log.info("查询结果转换为Map完成，Map大小: {}", deviceInfoMap.size());

            // 4. 生成Excel文件
            log.info("开始生成Excel文件");
            String excelFilePath = generateExcel(deviceIds, deviceInfoMap, filePath);
            log.info("Excel文件生成成功: {}", excelFilePath);

            // 处理成功后移动文件到backup目录
            moveProcessedFile(filePath, true, config);
            return ProcessingResult.success("拓扑文件处理成功，生成Excel文件", deviceIds.size());
        } catch (Exception e) {
            log.error("拓扑文件处理失败: {}", filePath, e);
            // 处理失败后移动文件到error目录
            moveProcessedFile(filePath, false, config);
            return ProcessingResult.failure("拓扑文件处理失败", e);
        }
    }

    /**
     * 从inf文件中提取以"开始处理设备: "开头的设备ID
     */
    private List<String> extractDeviceIds(String filePath, FileMonitorConfig config) throws IOException {
        List<String> deviceIds = new ArrayList<>();

        // 输出系统编码信息
        System.out.println("系统默认编码: " + System.getProperty("file.encoding"));
        System.out.println("系统默认字符集: " + Charset.defaultCharset().name());

        // 从配置中获取编码，默认使用UTF-8
        String encoding = "UTF-8";
        if (config.getCustomProperties() != null && config.getCustomProperties().containsKey("encoding")) {
            encoding = (String) config.getCustomProperties().get("encoding");
        }
        log.info("使用编码 {} 读取文件: {}", encoding, filePath);
        System.out.println("配置的编码: " + encoding);

        // 输出文件基本信息
        File file = new File(filePath);
        System.out.println("文件路径: " + filePath);
        System.out.println("文件大小: " + file.length() + " 字节");
        System.out.println("文件是否存在: " + file.exists());

        // 尝试使用多种编码读取文件前几行，比较结果
        String[] testEncodings = { "UTF-8", "GBK", "GB2312", "ISO-8859-1" };
        for (String testEncoding : testEncodings) {
            System.out.println("\n--- 尝试使用编码 " + testEncoding + " 读取前5行 --- ");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(filePath), testEncoding))) {
                for (int i = 0; i < 5; i++) {
                    String line = reader.readLine();
                    if (line == null)
                        break;
                    System.out.println("line " + (i + 1) + ": " + line);
                    System.out.println("  startIndex: " + line.indexOf("开始处理设备: "));
                }
            } catch (Exception e) {
                System.out.println("  读取失败: " + e.getMessage());
            }
        }

        // 使用配置的编码读取完整文件
        System.out.println("\n--- 使用配置的编码 " + encoding + " 读取完整文件 --- ");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(filePath), encoding))) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                // 查找包含"开始处理设备: "的行
                int startIndex = line.indexOf("开始处理设备: ");
                if (lineNum < 5)
                    System.out.println("line " + lineNum + ": " + line + " ; startIndex:" + startIndex);

                if (startIndex != -1) {
                    // 提取设备ID，格式为"开始处理设备: 5629499534275879 ，上游设备 ..."
                    String subLine = line.substring(startIndex + "开始处理设备: ".length());
                    // 提取第一个空格前的内容作为设备ID
                    int endIndex = subLine.indexOf(" ");
                    if (endIndex != -1) {
                        String deviceId = subLine.substring(0, endIndex).trim();
                        if (!deviceId.startsWith("53")) {
                            deviceIds.add(deviceId);
                        }
                    }
                }
            }
        }

        System.out.println("\n--- 提取结果 --- ");
        System.out.println("共提取到设备ID数量: " + deviceIds.size());

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

        // 2. 对比馈线名称，标记馈线是否变动
        if (excelData.size() > 1) {
            for (int i = 1; i < excelData.size(); i++) {
                DeviceTopoInfo current = excelData.get(i);
                DeviceTopoInfo previous = excelData.get(i - 1);
                
                // 对比当前行和上一行的馈线名称
                boolean isChange = (current.getFeederName() == null && previous.getFeederName() != null) ||
                                  (current.getFeederName() != null && previous.getFeederName() == null) ||
                                  (current.getFeederName() != null && previous.getFeederName() != null && 
                                   !current.getFeederName().equals(previous.getFeederName()));
                
                // 如果变动，设置馈线是否变动字段为"变动"
                if (isChange) {
                    current.setFeederChange("变动");
                    previous.setFeederChange("变动");
                }
            }
        }

        // 3. 生成文件名
        String fileName = "设备拓扑信息_" + System.currentTimeMillis() + ".xlsx";
        String fullPath = excelExportPath + java.io.File.separator + fileName;

        // 3. 创建目录（如果不存在）
        java.io.File directory = new java.io.File(excelExportPath);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        // 4. 使用EasyExcel生成Excel文件
        EasyExcel.write(fullPath, DeviceTopoInfo.class)
                .sheet("设备拓扑信息")
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