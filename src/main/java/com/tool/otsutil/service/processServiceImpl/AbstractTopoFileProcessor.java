package com.tool.otsutil.service.processServiceImpl;

import com.alibaba.excel.EasyExcel;
import com.tool.otsutil.config.FileMonitorConfig;
import com.tool.otsutil.mapper.TopoDeviceMapper;
import com.tool.otsutil.model.dto.DeviceTopoInfo;
import com.tool.otsutil.model.dto.TopoDeviceRecord;
import com.tool.otsutil.model.fileProcess.FileEvent;
import com.tool.otsutil.model.fileProcess.ProcessingResult;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
public abstract class AbstractTopoFileProcessor extends AbstractFileProcessor {

    private static final int QUERY_BATCH_SIZE = 1000;

    private final TopoDeviceMapper topoDeviceMapper;
    private final String excelExportPath;

    protected AbstractTopoFileProcessor(TopoDeviceMapper topoDeviceMapper, String excelExportPath) {
        this.topoDeviceMapper = topoDeviceMapper;
        this.excelExportPath = excelExportPath;
    }

    @Override
    public ProcessingResult process(String filePath, FileEvent event, FileMonitorConfig config) {
        try {
            log.info("处理{}: {}", getFileDescription(), filePath);

            List<String> deviceIds = extractDeviceIds(Paths.get(filePath), config);
            log.info("提取到设备ID数量: {}", deviceIds.size());

            if (deviceIds.isEmpty()) {
                return ProcessingResult.success(getFileDescription() + "处理成功，未提取到设备ID", 0);
            }

            Map<String, TopoDeviceRecord> deviceInfoMap = loadDeviceInfo(deviceIds);
            String excelFilePath = generateExcel(deviceIds, deviceInfoMap);
            log.info("Excel文件生成成功: {}", excelFilePath);

            return ProcessingResult.success(getFileDescription() + "处理成功，生成Excel文件", deviceIds.size());
        } catch (Exception e) {
            log.error("{}处理失败: {}", getFileDescription(), filePath, e);
            return ProcessingResult.failure(getFileDescription() + "处理失败", e);
        }
    }

    protected abstract List<String> extractDeviceIds(Path filePath, FileMonitorConfig config) throws IOException;

    protected abstract String getFileDescription();

    protected abstract String getExcelFilePrefix();

    protected abstract String getSheetName();

    protected boolean shouldMarkFeederChange() {
        return false;
    }

    private Map<String, TopoDeviceRecord> loadDeviceInfo(List<String> deviceIds) {
        log.info("开始从数据库查询设备信息，设备ID数量: {}", deviceIds.size());
        Map<String, TopoDeviceRecord> deviceInfoMap = new LinkedHashMap<>();

        for (int i = 0; i < deviceIds.size(); i += QUERY_BATCH_SIZE) {
            int endIndex = Math.min(i + QUERY_BATCH_SIZE, deviceIds.size());
            List<String> batchIds = deviceIds.subList(i, endIndex);
            log.info("处理批次: {}-{}/{}", i + 1, endIndex, deviceIds.size());

            for (TopoDeviceRecord deviceInfo : topoDeviceMapper.selectDeviceInfoByIds(batchIds)) {
                deviceInfoMap.put(deviceInfo.getId(), deviceInfo);
            }
        }

        log.info("查询到设备信息数量: {}", deviceInfoMap.size());
        return deviceInfoMap;
    }

    private String generateExcel(List<String> deviceIds, Map<String, TopoDeviceRecord> deviceInfoMap) throws IOException {
        List<DeviceTopoInfo> excelData = buildExcelData(deviceIds, deviceInfoMap);
        if (shouldMarkFeederChange()) {
            markFeederChanges(excelData);
        }

        Path exportDir = Paths.get(excelExportPath);
        Files.createDirectories(exportDir);

        Path outputFile = exportDir.resolve(getExcelFilePrefix() + "_" + System.currentTimeMillis() + ".xlsx");
        EasyExcel.write(outputFile.toString(), DeviceTopoInfo.class)
                .sheet(getSheetName())
                .doWrite(excelData);
        return outputFile.toString();
    }

    private List<DeviceTopoInfo> buildExcelData(List<String> deviceIds, Map<String, TopoDeviceRecord> deviceInfoMap) {
        List<DeviceTopoInfo> excelData = new ArrayList<>(deviceIds.size());
        for (String deviceId : deviceIds) {
            DeviceTopoInfo deviceTopoInfo = new DeviceTopoInfo();
            deviceTopoInfo.setId(deviceId);

            TopoDeviceRecord deviceInfo = deviceInfoMap.get(deviceId);
            if (deviceInfo != null) {
                deviceTopoInfo.setDeviceName(deviceInfo.getDeviceName());
                deviceTopoInfo.setFeederName(deviceInfo.getFeederName());
            }

            excelData.add(deviceTopoInfo);
        }
        return excelData;
    }

    private void markFeederChanges(List<DeviceTopoInfo> excelData) {
        for (int i = 1; i < excelData.size(); i++) {
            DeviceTopoInfo current = excelData.get(i);
            DeviceTopoInfo previous = excelData.get(i - 1);
            if (!Objects.equals(current.getFeederName(), previous.getFeederName())) {
                current.setFeederChange("变动");
                previous.setFeederChange("变动");
            }
        }
    }
}
