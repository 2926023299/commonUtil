package com.tool.otsutil.service.processServiceImpl;

import com.tool.otsutil.config.FileMonitorConfig;
import com.tool.otsutil.mapper.TopoDeviceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 拓扑文件处理器
 */
@Component
@Slf4j
public class TopoFileProcessorImpl extends AbstractTopoFileProcessor {

    private static final Pattern DEVICE_ID_PATTERN = Pattern.compile("开始处理设备:\\s*(\\d+)");

    public TopoFileProcessorImpl(TopoDeviceMapper topoDeviceMapper,
                                 @Value("${excel.export-path}") String excelExportPath) {
        super(topoDeviceMapper, excelExportPath);
    }

    @Override
    public String getName() {
        return "topo-processor";
    }

    @Override
    public boolean supports(String filePath, boolean isStable) {
        return filePath.toLowerCase().endsWith(".inf") && isStable;
    }

    @Override
    protected List<String> extractDeviceIds(Path filePath, FileMonitorConfig config) throws IOException {
        List<String> deviceIds = new ArrayList<>();
        Charset charset = resolveCharset(config);

        log.info("使用编码 {} 读取文件: {}", charset.name(), filePath);
        try (BufferedReader reader = Files.newBufferedReader(filePath, charset)) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher matcher = DEVICE_ID_PATTERN.matcher(line);
                if (matcher.find()) {
                    String deviceId = matcher.group(1);
                    if (!deviceId.startsWith("53")) {
                        deviceIds.add(deviceId);
                    }
                }
            }
        }
        return deviceIds;
    }

    @Override
    protected String getFileDescription() {
        return "拓扑文件";
    }

    @Override
    protected String getExcelFilePrefix() {
        return "设备拓扑信息";
    }

    @Override
    protected String getSheetName() {
        return "设备拓扑信息";
    }

    @Override
    protected boolean shouldMarkFeederChange() {
        return true;
    }

    private Charset resolveCharset(FileMonitorConfig config) {
        if (config.getCustomProperties() == null) {
            return StandardCharsets.UTF_8;
        }

        Object encoding = config.getCustomProperties().get("encoding");
        if (encoding == null) {
            return StandardCharsets.UTF_8;
        }

        try {
            return Charset.forName(String.valueOf(encoding));
        } catch (Exception ex) {
            log.warn("无效编码配置 {}, 使用UTF-8回退", encoding);
            return StandardCharsets.UTF_8;
        }
    }
}
