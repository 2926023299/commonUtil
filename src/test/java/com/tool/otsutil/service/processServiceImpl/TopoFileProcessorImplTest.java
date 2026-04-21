package com.tool.otsutil.service.processServiceImpl;

import com.tool.otsutil.config.FileMonitorConfig;
import com.tool.otsutil.mapper.TopoDeviceMapper;
import com.tool.otsutil.model.dto.TopoDeviceRecord;
import com.tool.otsutil.model.fileProcess.ProcessingResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

class TopoFileProcessorImplTest {

    @TempDir
    Path tempDir;

    @Test
    void processShouldGenerateExcelWithoutArchivingSourceFile() throws IOException {
        TopoDeviceMapper topoDeviceMapper = Mockito.mock(TopoDeviceMapper.class);
        TopoFileProcessorImpl processor = new TopoFileProcessorImpl(topoDeviceMapper, tempDir.resolve("excel").toString());

        TopoDeviceRecord first = new TopoDeviceRecord();
        first.setId("5629499534275879");
        first.setDeviceName("设备A");
        first.setFeederName("馈线1");

        TopoDeviceRecord second = new TopoDeviceRecord();
        second.setId("5629499534275880");
        second.setDeviceName("设备B");
        second.setFeederName("馈线2");

        when(topoDeviceMapper.selectDeviceInfoByIds(anyList())).thenReturn(Arrays.asList(first, second));

        Path monitorDir = Files.createDirectories(tempDir.resolve("monitor"));
        Path sourceFile = monitorDir.resolve("demo.inf");
        Files.write(sourceFile, Arrays.asList(
                "开始处理设备: 5629499534275879 上游设备 A",
                "开始处理设备: 5399999999999999 上游设备 B",
                "开始处理设备: 5629499534275880 上游设备 C"
        ), Charset.forName("GBK"));

        HashMap<String, Object> customProperties = new HashMap<>();
        customProperties.put("encoding", "GBK");
        FileMonitorConfig config = FileMonitorConfig.builder()
                .directory(monitorDir.toString())
                .customProperties(customProperties)
                .build();

        ProcessingResult result = processor.process(sourceFile.toString(), null, config);

        assertTrue(result.isSuccess());
        assertEquals(2, result.getData());
        assertTrue(Files.exists(sourceFile));
        assertEquals(1L, Files.list(tempDir.resolve("excel")).count());
    }

    @Test
    void processShouldReturnFailureWhenQueryFails() throws IOException {
        TopoDeviceMapper topoDeviceMapper = Mockito.mock(TopoDeviceMapper.class);
        TopoFileProcessorImpl processor = new TopoFileProcessorImpl(topoDeviceMapper, tempDir.resolve("excel").toString());
        when(topoDeviceMapper.selectDeviceInfoByIds(anyList())).thenThrow(new IllegalStateException("db down"));

        Path monitorDir = Files.createDirectories(tempDir.resolve("monitor-error"));
        Path sourceFile = monitorDir.resolve("demo.inf");
        Files.write(sourceFile, Arrays.asList("开始处理设备: 5629499534275879 上游设备 A"), Charset.forName("GBK"));

        HashMap<String, Object> customProperties = new HashMap<>();
        customProperties.put("encoding", "GBK");
        FileMonitorConfig config = FileMonitorConfig.builder()
                .directory(monitorDir.toString())
                .customProperties(customProperties)
                .build();

        ProcessingResult result = processor.process(sourceFile.toString(), null, config);

        assertFalse(result.isSuccess());
        assertTrue(Files.exists(sourceFile));
    }
}
