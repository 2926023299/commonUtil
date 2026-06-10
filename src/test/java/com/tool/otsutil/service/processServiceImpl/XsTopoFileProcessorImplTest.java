package com.tool.otsutil.service.processServiceImpl;

import com.tool.otsutil.config.FileMonitorConfig;
import com.tool.otsutil.mapper.TopoDeviceMapper;
import com.tool.otsutil.model.dto.TopoDeviceRecord;
import com.tool.otsutil.model.dto.TopoFeederRecord;
import com.tool.otsutil.model.fileProcess.ProcessingResult;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class XsTopoFileProcessorImplTest {

    @TempDir
    Path tempDir;

    @Test
    void processShouldExportDetailedEventsForNewLargeFileFormat() throws IOException {
        TopoDeviceMapper topoDeviceMapper = Mockito.mock(TopoDeviceMapper.class);
        XsTopoFileProcessorImpl processor = new XsTopoFileProcessorImpl(topoDeviceMapper, tempDir.resolve("excel").toString());

        mockLookup(topoDeviceMapper,
                Arrays.asList(feeder("18014398509483238", "主馈线A")),
                Arrays.asList(
                        device("4503599627412982", "出线开关A", "主馈线A"),
                        device("4503599627412966", "量测设备A", "主馈线A"),
                        device("5629499678061364", "设备A", "主馈线A"),
                        device("4785074604248238", "边界设备A", "联络馈线")
                ));

        Path monitorDir = Files.createDirectories(tempDir.resolve("monitor"));
        Path sourceFile = monitorDir.resolve("modelmanage.log");
        Files.write(sourceFile, Arrays.asList(
                "2026-03-24 00:10:11 开始拓扑:18014398509483238,4503599627412982",
                "2026-03-24 00:10:11 获取key[rtdb:4504613239694838]的值: {\"mansetFlag\":\"0\",\"updateTime\":\"2026-03-23 23:57:51\",\"time\":\"3826-10-20 06:23:20\",\"value\":\"1\"}",
                "2026-03-24 00:10:11 equip:5629499678061364",
                "2026-03-24 00:10:11 key[rtdb:4786088216512130]不存在",
                "2026-03-24 00:10:11 找到边界设备4785074604248238,终止分支拓扑",
                "2026-03-24 00:10:12 线路18014398509483238拓扑结束,已完成数5"
        ), Charset.forName("GBK"));

        FileMonitorConfig config = FileMonitorConfig.builder()
                .directory(monitorDir.toString())
                .build();

        ProcessingResult result = processor.process(sourceFile.toString(), null, config);

        assertTrue(result.isSuccess());
        assertEquals(6L, ((Number) result.getData()).longValue());

        Path excelFile = Files.list(tempDir.resolve("excel")).findFirst().orElseThrow(IllegalStateException::new);
        ExcelSnapshot snapshot = readExcel(excelFile);

        assertEquals(1, snapshot.sheetNames.size());
        assertEquals("XS拓扑信息-1", snapshot.sheetNames.get(0));

        List<List<String>> rows = snapshot.rows;
        assertEquals(7, rows.size());

        assertEquals("馈线开始", rows.get(1).get(2));
        assertEquals("主馈线A", rows.get(1).get(5));
        assertEquals("4503599627412982", rows.get(1).get(6));
        assertEquals("出线开关A", rows.get(1).get(7));

        assertEquals("量测状态", rows.get(2).get(2));
        assertEquals("4503599627412982", rows.get(2).get(6));
        assertEquals("出线开关A", rows.get(2).get(7));
        assertEquals("4504613239694838", rows.get(2).get(9));
        assertEquals("合", rows.get(2).get(10));

        assertEquals("设备经过", rows.get(3).get(2));
        assertEquals("5629499678061364", rows.get(3).get(6));
        assertEquals("设备A", rows.get(3).get(7));

        assertEquals("量测缺失", rows.get(4).get(2));
        assertEquals("4785074604230274", rows.get(4).get(6));
        assertEquals("", rows.get(4).get(7));
        assertEquals("4786088216512130", rows.get(4).get(9));
        assertEquals("不存在", rows.get(4).get(10));
        assertEquals("量测对应设备未识别，且redis分合位不存在", rows.get(4).get(3));

        assertEquals("边界设备", rows.get(5).get(2));
        assertEquals("4785074604248238", rows.get(5).get(6));
        assertEquals("边界设备A", rows.get(5).get(7));

        assertEquals("拓扑结束", rows.get(6).get(2));
        assertEquals("当前馈线拓扑结束，已完成数5", rows.get(6).get(3));
        assertEquals("18014398509483238", rows.get(6).get(4));
        assertEquals("主馈线A", rows.get(6).get(5));
        assertEquals("", rows.get(6).get(6));
    }

    @Test
    void processShouldSplitIntoMultipleSheetsWhenRowLimitExceeded() throws IOException {
        TopoDeviceMapper topoDeviceMapper = Mockito.mock(TopoDeviceMapper.class);
        XsTopoFileProcessorImpl processor = new XsTopoFileProcessorImpl(topoDeviceMapper, tempDir.resolve("excel-split").toString()) {
            @Override
            protected int getSheetRowLimit() {
                return 2;
            }

            @Override
            protected int getWriteBatchSize() {
                return 1;
            }
        };

        mockLookup(topoDeviceMapper,
                Arrays.asList(feeder("18014398509483238", "主馈线A")),
                Arrays.asList(
                        device("4503599627412982", "出线开关A", "主馈线A"),
                        device("5629499678061364", "设备A", "主馈线A"),
                        device("5629499678061365", "设备B", "主馈线A"),
                        device("5629499678061366", "设备C", "主馈线A")
                ));

        Path monitorDir = Files.createDirectories(tempDir.resolve("monitor-split"));
        Path sourceFile = monitorDir.resolve("modelmanage.log");
        Files.write(sourceFile, Arrays.asList(
                "2026-03-24 00:10:11 开始拓扑:18014398509483238,4503599627412982",
                "2026-03-24 00:10:11 equip:5629499678061364",
                "2026-03-24 00:10:11 equip:5629499678061365",
                "2026-03-24 00:10:11 equip:5629499678061366",
                "2026-03-24 00:10:12 线路18014398509483238拓扑结束,已完成数5"
        ), Charset.forName("GBK"));

        ProcessingResult result = processor.process(sourceFile.toString(), null, FileMonitorConfig.builder()
                .directory(monitorDir.toString())
                .build());

        assertTrue(result.isSuccess());
        Path excelFile = Files.list(tempDir.resolve("excel-split")).findFirst().orElseThrow(IllegalStateException::new);
        ExcelSnapshot snapshot = readExcel(excelFile);

        assertEquals(3, snapshot.sheetNames.size());
        assertEquals("XS拓扑信息-1", snapshot.sheetNames.get(0));
        assertEquals("XS拓扑信息-2", snapshot.sheetNames.get(1));
        assertEquals("XS拓扑信息-3", snapshot.sheetNames.get(2));
    }

    @Test
    void processShouldExportTransferEvents() throws IOException {
        TopoDeviceMapper topoDeviceMapper = Mockito.mock(TopoDeviceMapper.class);
        XsTopoFileProcessorImpl processor = new XsTopoFileProcessorImpl(topoDeviceMapper, tempDir.resolve("excel-transfer").toString());

        mockLookup(topoDeviceMapper,
                Arrays.asList(
                        feeder("18014398626923766", "源馈线"),
                        feeder("18014398626923853", "对侧馈线")
                ),
                Arrays.asList(
                        device("4503599744831822", "联络开关A", "源馈线"),
                        device("5629499652069409", "联络后设备A", "对侧馈线"),
                        device("4785074721601590", "联络后设备B", "对侧馈线")
                ));

        Path monitorDir = Files.createDirectories(tempDir.resolve("monitor-transfer"));
        Path sourceFile = monitorDir.resolve("modelmanage.log");
        Files.write(sourceFile, Arrays.asList(
                "2026-03-27 00:10:23 线路18014398626923766的联络开关4503599744831822发生合位转电",
                "2026-03-27 00:10:23 拓扑联络开关4503599744831822后线路",
                "2026-03-27 00:10:23 联络后_equip:5629499652069409",
                "2026-03-27 00:10:23 联络后_equip:4785074721601590",
                "2026-03-27 00:10:23 获取key[rtdb:4786088333883446]的值: {\"mansetFlag\":\"1\",\"updateTime\":\"2026-03-27 00:07:54\",\"time\":\"2023-06-08 14:28:10\",\"value\":\"1\"}",
                "2026-03-27 00:10:23 根据设备4785074721601590映射到对侧线路18014398626923853",
                "2026-03-27 00:10:23 update ies_xs.calc_desc set PackFlag=1,PackID1=18014398626923853 where datetime=1774454400 and id=18014398626923766",
                "2026-03-27 00:10:23 update ies_xs.calc_desc set PackFlag=1 where datetime=1774454400 and id=18014398626923853",
                "2026-03-27 00:10:23 联络后_equip:5629499652069409"
        ), StandardCharsets.UTF_8);

        ProcessingResult result = processor.process(sourceFile.toString(), null, FileMonitorConfig.builder()
                .directory(monitorDir.toString())
                .build());

        assertTrue(result.isSuccess());
        assertEquals(9L, ((Number) result.getData()).longValue());

        Path excelFile = Files.list(tempDir.resolve("excel-transfer")).findFirst().orElseThrow(IllegalStateException::new);
        ExcelSnapshot snapshot = readExcel(excelFile);
        List<List<String>> rows = snapshot.rows;

        assertEquals("转电开始", rows.get(1).get(2));
        assertEquals("18014398626923766", rows.get(1).get(4));
        assertEquals("4503599744831822", rows.get(1).get(6));

        assertEquals("联络后线路开始", rows.get(2).get(2));
        assertEquals("4503599744831822", rows.get(2).get(6));

        assertEquals("联络后设备", rows.get(3).get(2));
        assertEquals("5629499652069409", rows.get(3).get(6));
        assertEquals("联络后设备A", rows.get(3).get(7));

        assertEquals("对侧线路映射", rows.get(6).get(2));
        assertEquals("4785074721601590", rows.get(6).get(6));
        assertEquals("联络后设备B", rows.get(6).get(7));
        assertEquals("对侧馈线", rows.get(6).get(8));

        assertEquals("转电更新SQL", rows.get(7).get(2));
        assertTrue(rows.get(7).get(3).contains("update ies_xs.calc_desc"));
        assertEquals("18014398626923766", rows.get(7).get(4));

        assertEquals("转电更新SQL", rows.get(8).get(2));
        assertEquals("18014398626923853", rows.get(8).get(4));
    }

    @Test
    void processShouldAutoDetectUtf8LogWhenNoEncodingConfigured() throws IOException {
        TopoDeviceMapper topoDeviceMapper = Mockito.mock(TopoDeviceMapper.class);
        XsTopoFileProcessorImpl processor = new XsTopoFileProcessorImpl(topoDeviceMapper, tempDir.resolve("excel-utf8").toString());

        mockLookup(topoDeviceMapper,
                Arrays.asList(feeder("18014398509483238", "主馈线A")),
                Arrays.asList(device("4503599627412982", "出线开关A", "主馈线A")));

        Path monitorDir = Files.createDirectories(tempDir.resolve("monitor-utf8"));
        Path sourceFile = monitorDir.resolve("modelmanage.log");
        Files.write(sourceFile, Arrays.asList(
                "2026-03-24 00:10:11 开始拓扑:18014398509483238,4503599627412982",
                "2026-03-24 00:10:12 线路18014398509483238拓扑结束,已完成数1"
        ), StandardCharsets.UTF_8);

        ProcessingResult result = processor.process(sourceFile.toString(), null, FileMonitorConfig.builder()
                .directory(monitorDir.toString())
                .build());

        assertTrue(result.isSuccess());
        assertEquals(2L, ((Number) result.getData()).longValue());
        Path excelFile = Files.list(tempDir.resolve("excel-utf8")).findFirst().orElseThrow(IllegalStateException::new);
        ExcelSnapshot snapshot = readExcel(excelFile);
        assertEquals("馈线开始", snapshot.rows.get(1).get(2));
        assertEquals("拓扑结束", snapshot.rows.get(2).get(2));
    }

    @Test
    void processShouldParseNewLineTypesFromSingleLog() throws IOException {
        TopoDeviceMapper topoDeviceMapper = Mockito.mock(TopoDeviceMapper.class);
        XsTopoFileProcessorImpl processor = new XsTopoFileProcessorImpl(topoDeviceMapper, tempDir.resolve("excel-single").toString());

        mockLookup(topoDeviceMapper,
                Arrays.asList(feeder("18014398643712526", "馈线A")),
                Arrays.asList(
                        device("4503599763218001", "出线开关A", "馈线A"),
                        device("5629499678140750", "设备A", "馈线A"),
                        device("4503599728073085", "边界设备A", "馈线A"),
                        device("12384899075938262", "末端设备A", "馈线A"),
                        device("9288675865318470", "站房母线A", "馈线A")
                ));

        Path monitorDir = Files.createDirectories(tempDir.resolve("monitor-single"));
        Path sourceFile = monitorDir.resolve("modelmanage.log");
        Files.write(sourceFile, Arrays.asList(
                "2026-06-04 18:02:35 start_equip:18014398643712526,4503599763218001",
                "2026-06-04 18:02:35 equip:5629499678140750",
                "2026-06-04 18:02:35 select Value from ies_ls.conditionevent202605_05 where Event_Type in (2,3) and ResourceID=4503599728073085 and event_time>=date '2026-05-27' order by event_time asc",
                "2026-06-04 18:02:43 未在当前周表获取到设备4503599728073085的历史状态",
                "2026-06-04 18:02:51 获取到设备4503599728073085的最接近计算日期的历史状态取反:1",
                "2026-06-04 18:02:51 找到边界设备12384899075938262,终止分支拓扑",
                "2026-06-04 18:03:00 9288675865318470是站内母线连接点",
                "2026-06-04 18:03:00 线路18014398643712526拓扑结束,已完成数5"
        ), StandardCharsets.UTF_8);

        ProcessingResult result = processor.process(sourceFile.toString(), null, FileMonitorConfig.builder()
                .directory(monitorDir.toString())
                .build());

        assertTrue(result.isSuccess());
        assertEquals(8L, ((Number) result.getData()).longValue());

        Path excelFile = Files.list(tempDir.resolve("excel-single")).findFirst().orElseThrow(IllegalStateException::new);
        ExcelSnapshot snapshot = readExcel(excelFile);
        List<List<String>> rows = snapshot.rows;

        assertEquals(9, rows.size());

        assertEquals("馈线开始", rows.get(1).get(2));
        assertEquals("18014398643712526", rows.get(1).get(4));
        assertEquals("4503599763218001", rows.get(1).get(6));

        assertEquals("设备经过", rows.get(2).get(2));
        assertEquals("5629499678140750", rows.get(2).get(6));
        assertEquals("设备A", rows.get(2).get(7));

        assertEquals("分合闸事项查询", rows.get(3).get(2));
        assertEquals("4503599728073085", rows.get(3).get(6));
        assertTrue(rows.get(3).get(3).contains("select Value from"));
        assertTrue(rows.get(3).get(3).contains("conditionevent202605_05"));
        assertTrue(rows.get(3).get(3).contains("ResourceID=4503599728073085"));

        assertEquals("周期内未取到", rows.get(4).get(2));
        assertEquals("4503599728073085", rows.get(4).get(6));
        assertTrue(rows.get(4).get(3).contains("未在当前周表"));

        assertEquals("历史状态取反", rows.get(5).get(2));
        assertEquals("4503599728073085", rows.get(5).get(6));
        assertEquals("边界设备A", rows.get(5).get(7));
        assertEquals("合", rows.get(5).get(10));

        assertEquals("边界设备", rows.get(6).get(2));
        assertEquals("12384899075938262", rows.get(6).get(6));

        assertEquals("站内母线连接点", rows.get(7).get(2));
        assertEquals("9288675865318470", rows.get(7).get(6));
        assertEquals("站房母线A", rows.get(7).get(7));

        assertEquals("拓扑结束", rows.get(8).get(2));
        assertEquals("18014398643712526", rows.get(8).get(4));
    }

    private TopoDeviceRecord device(String id, String deviceName, String feederName) {
        TopoDeviceRecord record = new TopoDeviceRecord();
        record.setId(id);
        record.setDeviceName(deviceName);
        record.setFeederName(feederName);
        return record;
    }

    private void mockLookup(TopoDeviceMapper topoDeviceMapper,
                            List<TopoFeederRecord> feeders,
                            List<TopoDeviceRecord> devices) {
        doAnswer(invocation -> {
            List<String> ids = invocation.getArgument(0);
            List<TopoFeederRecord> results = new ArrayList<TopoFeederRecord>();
            for (TopoFeederRecord feeder : feeders) {
                if (ids.contains(feeder.getId())) {
                    results.add(feeder);
                }
            }
            return results;
        }).when(topoDeviceMapper).selectFeederInfoByIds(anyList());

        doAnswer(invocation -> {
            List<String> ids = invocation.getArgument(0);
            List<TopoDeviceRecord> results = new ArrayList<TopoDeviceRecord>();
            for (TopoDeviceRecord device : devices) {
                if (ids.contains(device.getId())) {
                    results.add(device);
                }
            }
            return results;
        }).when(topoDeviceMapper).selectDeviceInfoByIds(anyList());
    }

    private TopoFeederRecord feeder(String id, String name) {
        TopoFeederRecord record = new TopoFeederRecord();
        record.setId(id);
        record.setName(name);
        return record;
    }

    private ExcelSnapshot readExcel(Path excelFile) throws IOException {
        List<String> sheetNames = new ArrayList<String>();
        List<List<String>> firstSheetRows = new ArrayList<List<String>>();
        DataFormatter formatter = new DataFormatter();
        try (InputStream inputStream = Files.newInputStream(excelFile);
             XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                sheetNames.add(sheet.getSheetName());
                if (sheetIndex == 0) {
                    for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                        Row row = sheet.getRow(rowIndex);
                        List<String> values = new ArrayList<String>();
                        for (int cellIndex = 0; cellIndex < 11; cellIndex++) {
                            if (row == null || row.getCell(cellIndex) == null) {
                                values.add("");
                            } else {
                                values.add(formatter.formatCellValue(row.getCell(cellIndex)));
                            }
                        }
                        firstSheetRows.add(values);
                    }
                }
            }
        }
        return new ExcelSnapshot(sheetNames, firstSheetRows);
    }

    private static class ExcelSnapshot {
        private final List<String> sheetNames;
        private final List<List<String>> rows;

        private ExcelSnapshot(List<String> sheetNames, List<List<String>> rows) {
            this.sheetNames = sheetNames;
            this.rows = rows;
        }
    }
}
