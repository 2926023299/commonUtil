package com.tool.otsutil.service.processServiceImpl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.tool.otsutil.config.FileMonitorConfig;
import com.tool.otsutil.mapper.TopoDeviceMapper;
import com.tool.otsutil.model.dto.TopoDeviceRecord;
import com.tool.otsutil.model.dto.TopoFeederRecord;
import com.tool.otsutil.model.dto.XsTopoEventExportRow;
import com.tool.otsutil.model.fileProcess.FileEvent;
import com.tool.otsutil.model.fileProcess.ProcessingResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.CountingInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * XS拓扑文件处理器，用于处理大文件新格式modelmanage.log文件
 */
@Component
@Slf4j
public class XsTopoFileProcessorImpl extends AbstractFileProcessor {

    private static final int CHARSET_DETECT_SAMPLE_SIZE = 1024 * 1024;
    private static final String[] CHARSET_HINT_PATTERNS = new String[]{
            "开始拓扑:",
            "获取key[rtdb:",
            "key[rtdb:",
            "不存在",
            "拓扑结束",
            "找到边界设备",
            "联络后_equip:",
            "发生合位转电",
            "映射到对侧线路",
            "update ies_xs.calc_desc",
            "equip:",
            "查询conditionevent",
            "tie_breaker_status",
            "光伏用户站房内开关",
            "判定转电",
            "未在当前周表获取到设备",
            "获取到设备",
            "历史状态取反",
            "是站内母线连接点",
            "select Value from"
    };

    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})");
    private static final Pattern START_TOPOLOGY_PATTERN = Pattern.compile("开始拓扑:(\\d+),(\\d+)");
    private static final Pattern LEGACY_START_PATTERN = Pattern.compile("start_equip:(\\d+),(\\d+)");
    private static final Pattern DEVICE_PATTERN = Pattern.compile("equip:(\\d+)");
    private static final Pattern TRANSFER_DEVICE_PATTERN = Pattern.compile("联络后_equip:(\\d+)");
    private static final Pattern TRANSFER_START_PATTERN = Pattern.compile("线路(\\d+)的联络开关(\\d+)发生合位转电");
    private static final Pattern TRANSFER_SECTION_START_PATTERN = Pattern.compile("拓扑联络开关(\\d+)后线路");
    private static final Pattern TRANSFER_MAPPING_PATTERN = Pattern.compile("根据设备(\\d+)映射到对侧线路(\\d+)");
    private static final Pattern RTDB_VALUE_PATTERN = Pattern.compile("获取key\\[rtdb:(\\d+)]的值:");
    private static final Pattern RTDB_MISSING_PATTERN = Pattern.compile("key\\[rtdb:(\\d+)]不存在");
    private static final Pattern VALUE_PATTERN = Pattern.compile("\"value\":\"([01])\"");
    private static final Pattern BOUNDARY_PATTERN = Pattern.compile("找到边界设备(\\d+),终止分支拓扑");
    private static final Pattern TOPOLOGY_END_PATTERN = Pattern.compile("线路(\\d+)拓扑结束,已完成数(\\d+)");
    private static final Pattern UPDATE_SQL_PATTERN = Pattern.compile("(?i)update\\s+ies_xs\\.calc_desc\\s+set\\s+.*");
    private static final Pattern UPDATE_SQL_TARGET_ID_PATTERN = Pattern.compile("(?i)\\bid=(\\d+)");
    private static final Pattern TIE_BREAKER_STATUS_PATTERN = Pattern.compile("tie_breaker_status表联络开关断面状态为(分闸|合闸)(,判定转电)?");
    private static final Pattern PV_USER_SWITCH_PATTERN = Pattern.compile("(\\d+)为光伏用户站房内开关,默认(合位|分位)");
    private static final Pattern CONDITION_EVENT_SELECT_PATTERN = Pattern.compile("(?i)select\\s+Value\\s+from\\s+(\\S+)\\s+where\\s+.*ResourceID=(\\d+).*");
    private static final Pattern DEVICE_NOT_IN_PERIOD_PATTERN = Pattern.compile("未在当前周表获取到设备(\\d+)的历史状态");
    private static final Pattern HISTORICAL_STATUS_REVERSE_PATTERN = Pattern.compile("获取到设备(\\d+)的最接近计算日期的历史状态取反:(\\d+)");
    private static final Pattern STATION_BUS_CONNECTION_PATTERN = Pattern.compile("(\\d+)是站内母线连接点");

    private final TopoDeviceMapper topoDeviceMapper;
    private final String excelExportPath;

    public XsTopoFileProcessorImpl(TopoDeviceMapper topoDeviceMapper,
                                   @Value("${excel.export-path}") String excelExportPath) {
        this.topoDeviceMapper = topoDeviceMapper;
        this.excelExportPath = excelExportPath;
    }

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
            Path path = Paths.get(filePath);
            Charset charset = resolveCharset(path, config);
            long totalBytes = Files.size(path);
            log.info("处理XS拓扑文件: {}, 编码: {}, 文件大小: {} 字节", filePath, charset.name(), totalBytes);

            LookupContext lookupContext = new LookupContext(topoDeviceMapper, getLookupBatchSize());
            StreamingContext streamingContext = new StreamingContext();
            Path outputPath = buildOutputPath();

            ParseResult parseResult;
            try (CountingInputStream countingInputStream = new CountingInputStream(new BufferedInputStream(Files.newInputStream(path)));
                 BufferedReader reader = new BufferedReader(new InputStreamReader(countingInputStream, charset), 64 * 1024);
                 XsTopoExcelWriter excelWriter = new XsTopoExcelWriter(outputPath, getWriteBatchSize(), getSheetRowLimit())) {
                parseResult = parseAndWrite(reader, countingInputStream, totalBytes, excelWriter, lookupContext, streamingContext);
            }

            if (parseResult.totalRows == 0) {
                Files.deleteIfExists(outputPath);
                return ProcessingResult.success("XS拓扑文件处理成功，未识别到有效事件", 0);
            }

            log.info("XS拓扑Excel生成成功: {}, 有效事件数: {}, 总批次: {}, 总行数: {}, 最终sheet数: {}",
                    outputPath,
                    parseResult.totalRows,
                    parseResult.batchCount,
                    parseResult.totalLines,
                    parseResult.sheetCount);
            return ProcessingResult.success("XS拓扑文件处理成功，生成Excel文件", parseResult.totalRows);
        } catch (Exception e) {
            log.error("XS拓扑文件处理失败: {}", filePath, e);
            return ProcessingResult.failure("XS拓扑文件处理失败", e);
        }
    }

    protected int getWriteBatchSize() {
        return 1000;
    }

    protected int getSheetRowLimit() {
        return 1_000_000;
    }

    protected int getEventBatchSize() {
        return 5000;
    }

    protected int getLookupBatchSize() {
        return 1000;
    }

    private ParseResult parseAndWrite(BufferedReader reader,
                                      CountingInputStream countingInputStream,
                                      long totalBytes,
                                      XsTopoExcelWriter excelWriter,
                                      LookupContext lookupContext,
                                      StreamingContext streamingContext) throws IOException {
        ParseResult parseResult = new ParseResult();
        List<ParsedXsTopoEvent> eventBatch = new ArrayList<ParsedXsTopoEvent>(getEventBatchSize());

        String line;
        while ((line = reader.readLine()) != null) {
            parseResult.totalLines++;
            ParsedXsTopoEvent parsedEvent = parseLine(line, lookupContext, streamingContext);
            if (parsedEvent != null) {
                eventBatch.add(parsedEvent);
            }

            if (eventBatch.size() >= getEventBatchSize()) {
                flushBatch(eventBatch, excelWriter, lookupContext, parseResult, countingInputStream.getByteCount(), totalBytes);
            }
        }

        flushBatch(eventBatch, excelWriter, lookupContext, parseResult, countingInputStream.getByteCount(), totalBytes);
        parseResult.sheetCount = excelWriter.getCurrentSheetIndex();
        return parseResult;
    }

    private void flushBatch(List<ParsedXsTopoEvent> eventBatch,
                            XsTopoExcelWriter excelWriter,
                            LookupContext lookupContext,
                            ParseResult parseResult,
                            long processedBytes,
                            long totalBytes) {
        if (eventBatch.isEmpty()) {
            return;
        }

        lookupContext.prefetch(eventBatch);
        for (ParsedXsTopoEvent parsedEvent : eventBatch) {
            XsTopoEventExportRow row = buildExportRow(++parseResult.totalRows, parsedEvent, lookupContext);
            excelWriter.write(row);
        }

        eventBatch.clear();
        parseResult.batchCount++;
        logProgress(parseResult, processedBytes, totalBytes, excelWriter.getCurrentSheetIndex());
    }

    private void logProgress(ParseResult parseResult, long processedBytes, long totalBytes, int currentSheetIndex) {
        double percent = totalBytes <= 0 ? 0D : Math.min(100D, (processedBytes * 100D) / totalBytes);
        long estimatedTotalBatches = percent <= 0D ? 0L : Math.max(parseResult.batchCount, Math.round(parseResult.batchCount / (percent / 100D)));
        log.info("XS拓扑处理进度: 批次 {}/{}, 已读取 {} 行, 已输出 {} 条事件, 当前sheet {}, 进度 {}%",
                parseResult.batchCount,
                estimatedTotalBatches == 0L ? "?" : String.valueOf(estimatedTotalBatches),
                parseResult.totalLines,
                parseResult.totalRows,
                currentSheetIndex,
                String.format("%.2f", percent));
    }

    private ParsedXsTopoEvent parseLine(String line,
                                        LookupContext lookupContext,
                                        StreamingContext streamingContext) {
        String timestamp = extractTimestamp(line);

        Matcher transferStartMatcher = TRANSFER_START_PATTERN.matcher(line);
        if (transferStartMatcher.find()) {
            String feederId = transferStartMatcher.group(1);
            String switchId = transferStartMatcher.group(2);
            streamingContext.transferSourceFeederId = feederId;
            streamingContext.transferSwitchId = switchId;
            streamingContext.transferTargetFeederId = null;
            streamingContext.transferModeActive = false;
            return ParsedXsTopoEvent.transferStart(timestamp, feederId, switchId);
        }

        if (line.contains("查询conditionevent当天分合闸事项个数不相等,判定转电")) {
            return ParsedXsTopoEvent.textEvent(
                    XsTopoEventType.TRANSFER_CONDITION_DETERMINED,
                    timestamp,
                    resolveActiveFeederId(streamingContext),
                    "查询conditionevent当天分合闸事项个数不相等,判定转电");
        }

        Matcher transferSectionStartMatcher = TRANSFER_SECTION_START_PATTERN.matcher(line);
        if (transferSectionStartMatcher.find()) {
            String switchId = transferSectionStartMatcher.group(1);
            streamingContext.transferModeActive = true;
            if (streamingContext.transferSwitchId == null) {
                streamingContext.transferSwitchId = switchId;
            }
            return ParsedXsTopoEvent.transferSectionStart(timestamp, streamingContext.transferSourceFeederId, streamingContext.transferSwitchId);
        }

        Matcher startMatcher = START_TOPOLOGY_PATTERN.matcher(line);
        boolean startFound = startMatcher.find();
        if (!startFound) {
            startMatcher = LEGACY_START_PATTERN.matcher(line);
            startFound = startMatcher.find();
        }
        if (startFound) {
            String feederId = startMatcher.group(1);
            String outgoingSwitchId = startMatcher.group(2);
            streamingContext.currentFeederId = feederId;
            streamingContext.currentOutgoingSwitchId = outgoingSwitchId;
            streamingContext.clearTransferContext();
            return ParsedXsTopoEvent.start(timestamp, feederId, outgoingSwitchId);
        }

        Matcher transferMappingMatcher = TRANSFER_MAPPING_PATTERN.matcher(line);
        if (transferMappingMatcher.find()) {
            String deviceId = transferMappingMatcher.group(1);
            String targetFeederId = transferMappingMatcher.group(2);
            streamingContext.transferTargetFeederId = targetFeederId;
            return ParsedXsTopoEvent.transferMapping(timestamp, streamingContext.transferSourceFeederId, targetFeederId, deviceId);
        }

        Matcher topologyEndMatcher = TOPOLOGY_END_PATTERN.matcher(line);
        if (topologyEndMatcher.find()) {
            String feederId = topologyEndMatcher.group(1);
            String completedCount = topologyEndMatcher.group(2);
            if (feederId.equals(streamingContext.currentFeederId)) {
                streamingContext.clearNormalContext();
            }
            if (feederId.equals(streamingContext.transferSourceFeederId) || feederId.equals(streamingContext.transferTargetFeederId)) {
                streamingContext.clearTransferContext();
            }
            return ParsedXsTopoEvent.topologyEnd(timestamp, feederId, completedCount);
        }

        Matcher boundaryMatcher = BOUNDARY_PATTERN.matcher(line);
        if (boundaryMatcher.find()) {
            return ParsedXsTopoEvent.device(
                    XsTopoEventType.BOUNDARY,
                    timestamp,
                    resolveActiveFeederId(streamingContext),
                    boundaryMatcher.group(1),
                    null,
                    null,
                    "到达边界设备，终止当前分支");
        }

        Matcher transferDeviceMatcher = TRANSFER_DEVICE_PATTERN.matcher(line);
        if (transferDeviceMatcher.find()) {
            return ParsedXsTopoEvent.device(
                    XsTopoEventType.TRANSFER_DEVICE,
                    timestamp,
                    resolveTransferDisplayFeederId(streamingContext),
                    transferDeviceMatcher.group(1),
                    null,
                    null,
                    "联络后线路经过设备");
        }

        Matcher rtdbValueMatcher = RTDB_VALUE_PATTERN.matcher(line);
        if (rtdbValueMatcher.find()) {
            String measureId = rtdbValueMatcher.group(1);
            String derivedDeviceId = convertMeasureIdToDeviceId(measureId);
            Matcher valueMatcher = VALUE_PATTERN.matcher(line);
            if (valueMatcher.find()) {
                String status = mapSwitchStatus(valueMatcher.group(1));
                return ParsedXsTopoEvent.device(
                        streamingContext.transferModeActive ? XsTopoEventType.MEASURE_VALUE_TRANSFER : XsTopoEventType.MEASURE_VALUE,
                        timestamp,
                        resolveActiveFeederId(streamingContext),
                        derivedDeviceId,
                        measureId,
                        status,
                        null);
            }
        }

        Matcher rtdbMissingMatcher = RTDB_MISSING_PATTERN.matcher(line);
        if (rtdbMissingMatcher.find()) {
            String measureId = rtdbMissingMatcher.group(1);
            String derivedDeviceId = convertMeasureIdToDeviceId(measureId);
            return ParsedXsTopoEvent.device(
                    streamingContext.transferModeActive ? XsTopoEventType.MEASURE_MISSING_TRANSFER : XsTopoEventType.MEASURE_MISSING,
                    timestamp,
                    resolveActiveFeederId(streamingContext),
                    derivedDeviceId,
                    measureId,
                    "不存在",
                    null);
        }

        if (line.contains("查询conditionevent当天分合闸事项都为空")) {
            return ParsedXsTopoEvent.textEvent(
                    XsTopoEventType.CONDITION_EVENT_EMPTY,
                    timestamp,
                    resolveActiveFeederId(streamingContext),
                    "查询conditionevent当天分合闸事项都为空");
        }

        if (line.contains("tie_breaker_status")) {
            Matcher tieBreakerStatusMatcher = TIE_BREAKER_STATUS_PATTERN.matcher(line);
            if (tieBreakerStatusMatcher.find()) {
                String status = tieBreakerStatusMatcher.group(1);
                boolean triggersTransfer = tieBreakerStatusMatcher.group(2) != null;
                String desc = "tie_breaker_status表联络开关断面状态为" + status + (triggersTransfer ? ",判定转电" : "");
                return ParsedXsTopoEvent.textEvent(
                        XsTopoEventType.TIE_BREAKER_STATUS,
                        timestamp,
                        resolveActiveFeederId(streamingContext),
                        desc);
            }
        }

        Matcher updateSqlMatcher = UPDATE_SQL_PATTERN.matcher(line);
        if (updateSqlMatcher.find()) {
            String displayFeederId = extractUpdateSqlTargetFeederId(line);
            if (displayFeederId == null) {
                displayFeederId = resolveTransferDisplayFeederId(streamingContext);
            }
            return ParsedXsTopoEvent.transferUpdateSql(timestamp, displayFeederId, line);
        }

        Matcher deviceMatcher = DEVICE_PATTERN.matcher(line);
        if (deviceMatcher.find()) {
            return ParsedXsTopoEvent.device(
                    XsTopoEventType.DEVICE,
                    timestamp,
                    resolveActiveFeederId(streamingContext),
                    deviceMatcher.group(1),
                    null,
                    null,
                    "拓扑经过设备");
        }

        if (line.contains("为光伏用户站房内开关")) {
            Matcher pvUserSwitchMatcher = PV_USER_SWITCH_PATTERN.matcher(line);
            if (pvUserSwitchMatcher.find()) {
                String deviceId = pvUserSwitchMatcher.group(1);
                String defaultPosition = pvUserSwitchMatcher.group(2);
                return ParsedXsTopoEvent.device(
                        XsTopoEventType.PV_USER_SWITCH,
                        timestamp,
                        resolveActiveFeederId(streamingContext),
                        deviceId,
                        null,
                        defaultPosition,
                        "光伏用户站房内开关,默认" + defaultPosition);
            }
        }

        Matcher historicalStatusMatcher = HISTORICAL_STATUS_REVERSE_PATTERN.matcher(line);
        if (historicalStatusMatcher.find()) {
            String deviceId = historicalStatusMatcher.group(1);
            String reversedValue = historicalStatusMatcher.group(2);
            String status = mapSwitchStatus(reversedValue);
            return ParsedXsTopoEvent.device(
                    XsTopoEventType.HISTORICAL_STATUS_REVERSE,
                    timestamp,
                    resolveActiveFeederId(streamingContext),
                    deviceId,
                    null,
                    status,
                    "获取到设备最接近计算日期的历史状态取反:" + reversedValue);
        }

        Matcher deviceNotInPeriodMatcher = DEVICE_NOT_IN_PERIOD_PATTERN.matcher(line);
        if (deviceNotInPeriodMatcher.find()) {
            return ParsedXsTopoEvent.device(
                    XsTopoEventType.DEVICE_NOT_IN_PERIOD,
                    timestamp,
                    resolveActiveFeederId(streamingContext),
                    deviceNotInPeriodMatcher.group(1),
                    null,
                    null,
                    "未在当前周表获取到设备的历史状态");
        }

        Matcher stationBusMatcher = STATION_BUS_CONNECTION_PATTERN.matcher(line);
        if (stationBusMatcher.find()) {
            return ParsedXsTopoEvent.device(
                    XsTopoEventType.STATION_BUS_CONNECTION,
                    timestamp,
                    resolveActiveFeederId(streamingContext),
                    stationBusMatcher.group(1),
                    null,
                    null,
                    "站内母线连接点");
        }

        Matcher selectSqlMatcher = CONDITION_EVENT_SELECT_PATTERN.matcher(line);
        if (selectSqlMatcher.find()) {
            String resourceId = selectSqlMatcher.group(2);
            return ParsedXsTopoEvent.device(
                    XsTopoEventType.CONDITION_EVENT_QUERY,
                    timestamp,
                    resolveActiveFeederId(streamingContext),
                    resourceId,
                    null,
                    null,
                    line.trim());
        }

        return null;
    }

    private String resolveActiveFeederId(StreamingContext streamingContext) {
        if (streamingContext.transferModeActive) {
            return resolveTransferDisplayFeederId(streamingContext);
        }
        return streamingContext.currentFeederId;
    }

    private String resolveTransferDisplayFeederId(StreamingContext streamingContext) {
        return streamingContext.transferTargetFeederId != null ? streamingContext.transferTargetFeederId : streamingContext.transferSourceFeederId;
    }

    private XsTopoEventExportRow buildExportRow(long sequence,
                                                ParsedXsTopoEvent parsedEvent,
                                                LookupContext lookupContext) {
        XsTopoEventExportRow row = new XsTopoEventExportRow();
        row.setSequence((int) sequence);
        row.setTimestamp(parsedEvent.timestamp);
        row.setRecordType(parsedEvent.eventType.getDisplayName());
        row.setDescription(resolveDescription(parsedEvent, lookupContext));
        row.setFeederId(parsedEvent.feederId);

        TopoFeederRecord feederRecord = lookupContext.getCachedFeeder(parsedEvent.feederId);
        if (feederRecord != null) {
            row.setFeederName(feederRecord.getName());
        }

        switch (parsedEvent.eventType) {
            case START:
            case TRANSFER_START:
            case TRANSFER_SECTION_START:
                fillDeviceFromLookup(row, parsedEvent.outgoingSwitchId, lookupContext);
                if (row.getDeviceFeederName() == null) {
                    row.setDeviceFeederName(row.getFeederName());
                }
                break;
            case DEVICE:
            case TRANSFER_DEVICE:
            case BOUNDARY:
            case MEASURE_VALUE:
            case MEASURE_MISSING:
            case MEASURE_VALUE_TRANSFER:
            case MEASURE_MISSING_TRANSFER:
            case PV_USER_SWITCH:
            case HISTORICAL_STATUS_REVERSE:
            case DEVICE_NOT_IN_PERIOD:
            case STATION_BUS_CONNECTION:
            case CONDITION_EVENT_QUERY:
                fillDeviceFromLookup(row, parsedEvent.deviceId, lookupContext);
                break;
            case TRANSFER_MAPPING:
                fillDeviceFromLookup(row, parsedEvent.deviceId, lookupContext);
                if (parsedEvent.targetFeederId != null) {
                    TopoFeederRecord targetFeeder = lookupContext.getCachedFeeder(parsedEvent.targetFeederId);
                    row.setDeviceFeederName(targetFeeder != null ? targetFeeder.getName() : parsedEvent.targetFeederId);
                }
                break;
            case TRANSFER_UPDATE_SQL:
            case TOPOLOGY_END:
            default:
                break;
        }

        row.setMeasureId(parsedEvent.measureId);
        row.setSwitchStatus(parsedEvent.switchStatus);
        return row;
    }

    private void fillDeviceFromLookup(XsTopoEventExportRow row, String deviceId, LookupContext lookupContext) {
        row.setDeviceId(deviceId);
        TopoDeviceRecord deviceRecord = lookupContext.getCachedDevice(deviceId);
        if (deviceRecord != null) {
            row.setDeviceName(deviceRecord.getDeviceName());
            row.setDeviceFeederName(deviceRecord.getFeederName());
        }
    }

    private String resolveDescription(ParsedXsTopoEvent parsedEvent, LookupContext lookupContext) {
        switch (parsedEvent.eventType) {
            case START:
                return "开始解析馈线拓扑";
            case TRANSFER_START:
                return "联络开关发生合位转电";
            case TRANSFER_SECTION_START:
                return "开始解析联络开关后线路";
            case DEVICE:
                return parsedEvent.description;
            case BOUNDARY:
                return parsedEvent.description;
            case MEASURE_VALUE:
            case MEASURE_VALUE_TRANSFER:
                return lookupContext.getCachedDevice(parsedEvent.deviceId) != null
                        ? "量测分合位状态为" + parsedEvent.switchStatus
                        : "量测对应设备未识别，按量测ID输出分合位状态";
            case MEASURE_MISSING:
            case MEASURE_MISSING_TRANSFER:
                return lookupContext.getCachedDevice(parsedEvent.deviceId) != null
                        ? "设备对应的redis分合位不存在"
                        : "量测对应设备未识别，且redis分合位不存在";
            case TOPOLOGY_END:
                return "当前馈线拓扑结束，已完成数" + parsedEvent.completedCount;
            case TRANSFER_DEVICE:
                return parsedEvent.description;
            case TRANSFER_MAPPING:
                return buildTransferMappingDescription(parsedEvent, lookupContext);
            case TRANSFER_UPDATE_SQL:
                return parsedEvent.description;
            case CONDITION_EVENT_EMPTY:
                return parsedEvent.description;
            case TIE_BREAKER_STATUS:
                return parsedEvent.description;
            case TRANSFER_CONDITION_DETERMINED:
                return parsedEvent.description;
            case PV_USER_SWITCH:
                return parsedEvent.description;
            case HISTORICAL_STATUS_REVERSE:
                return parsedEvent.description;
            case DEVICE_NOT_IN_PERIOD:
                return parsedEvent.description;
            case STATION_BUS_CONNECTION:
                return parsedEvent.description;
            case CONDITION_EVENT_QUERY:
                return parsedEvent.description;
            default:
                return "";
        }
    }

    private String buildTransferMappingDescription(ParsedXsTopoEvent parsedEvent, LookupContext lookupContext) {
        if (parsedEvent.targetFeederId == null) {
            return "根据设备映射到对侧线路";
        }
        TopoFeederRecord targetFeeder = lookupContext.getCachedFeeder(parsedEvent.targetFeederId);
        if (targetFeeder != null) {
            return "根据设备映射到对侧线路: " + parsedEvent.targetFeederId + "(" + targetFeeder.getName() + ")";
        }
        return "根据设备映射到对侧线路: " + parsedEvent.targetFeederId;
    }

    private Charset resolveCharset(FileMonitorConfig config) {
        if (config == null || config.getCustomProperties() == null) {
            return null;
        }

        Object encoding = config.getCustomProperties().get("encoding");
        if (encoding == null) {
            return null;
        }

        try {
            return Charset.forName(String.valueOf(encoding));
        } catch (Exception ex) {
            log.warn("无效编码配置 {}, 使用GBK回退", encoding);
            return null;
        }
    }

    private Charset resolveCharset(Path path, FileMonitorConfig config) {
        Charset configuredCharset = resolveCharset(config);
        if (configuredCharset != null) {
            return configuredCharset;
        }

        try {
            Charset detectedCharset = detectCharset(path);
            log.info("自动探测日志编码结果: {}", detectedCharset.name());
            return detectedCharset;
        } catch (Exception ex) {
            log.warn("自动探测日志编码失败，使用GBK回退", ex);
            return Charset.forName("GBK");
        }
    }

    private Charset detectCharset(Path path) throws IOException {
        byte[] bytes;
        try (BufferedInputStream inputStream = new BufferedInputStream(Files.newInputStream(path))) {
            int sampleSize = (int) Math.min(CHARSET_DETECT_SAMPLE_SIZE, Files.size(path));
            bytes = new byte[sampleSize];
            int read = inputStream.read(bytes);
            if (read < sampleSize && read > 0) {
                bytes = Arrays.copyOf(bytes, read);
            }
        }

        if (hasUtf8Bom(bytes)) {
            return StandardCharsets.UTF_8;
        }

        Charset utf8 = StandardCharsets.UTF_8;
        Charset gbk = Charset.forName("GBK");
        int utf8Score = scoreDecodedSample(new String(bytes, utf8));
        int gbkScore = scoreDecodedSample(new String(bytes, gbk));

        log.info("日志编码探测得分: UTF-8={}, GBK={}", utf8Score, gbkScore);
        return utf8Score >= gbkScore ? utf8 : gbk;
    }

    private boolean hasUtf8Bom(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF;
    }

    private int scoreDecodedSample(String sample) {
        int score = 0;
        for (String pattern : CHARSET_HINT_PATTERNS) {
            int index = -1;
            while ((index = sample.indexOf(pattern, index + 1)) >= 0) {
                score += 10;
            }
        }

        for (int i = 0; i < sample.length(); i++) {
            if (sample.charAt(i) == '\uFFFD') {
                score -= 3;
            }
        }
        return score;
    }

    private String extractTimestamp(String line) {
        Matcher matcher = TIMESTAMP_PATTERN.matcher(line);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String mapSwitchStatus(String value) {
        return "1".equals(value) ? "合" : "分";
    }

    private String convertMeasureIdToDeviceId(String measureId) {
        BigInteger source = new BigInteger(measureId);
        String binary = source.toString(2);
        if (binary.length() < 64) {
            char[] padding = new char[64 - binary.length()];
            Arrays.fill(padding, '0');
            binary = new String(padding) + binary;
        }

        char[] chars = binary.toCharArray();
        for (int i = 16; i <= 31 && i < chars.length; i++) {
            chars[i] = '0';
        }

        return new BigInteger(new String(chars), 2).toString();
    }

    private String extractUpdateSqlTargetFeederId(String line) {
        Matcher matcher = UPDATE_SQL_TARGET_ID_PATTERN.matcher(line);
        String lastMatch = null;
        while (matcher.find()) {
            lastMatch = matcher.group(1);
        }
        return lastMatch;
    }

    private Path buildOutputPath() throws IOException {
        Path exportDir = Paths.get(excelExportPath);
        Files.createDirectories(exportDir);
        return exportDir.resolve("XS拓扑事件明细_" + System.currentTimeMillis() + ".xlsx");
    }

    private enum XsTopoEventType {
        START("馈线开始"),
        MEASURE_VALUE("量测状态"),
        MEASURE_MISSING("量测缺失"),
        DEVICE("设备经过"),
        BOUNDARY("边界设备"),
        TOPOLOGY_END("拓扑结束"),
        TRANSFER_START("转电开始"),
        TRANSFER_SECTION_START("联络后线路开始"),
        TRANSFER_DEVICE("联络后设备"),
        TRANSFER_MAPPING("对侧线路映射"),
        TRANSFER_UPDATE_SQL("转电更新SQL"),
        MEASURE_VALUE_TRANSFER("量测状态"),
        MEASURE_MISSING_TRANSFER("量测缺失"),
        CONDITION_EVENT_EMPTY("条件事项为空"),
        TIE_BREAKER_STATUS("断面状态查询"),
        TRANSFER_CONDITION_DETERMINED("条件判定转电"),
        PV_USER_SWITCH("光伏用户开关"),
        HISTORICAL_STATUS_REVERSE("历史状态取反"),
        DEVICE_NOT_IN_PERIOD("周期内未取到"),
        STATION_BUS_CONNECTION("站内母线连接点"),
        CONDITION_EVENT_QUERY("分合闸事项查询");

        private final String displayName;

        XsTopoEventType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static class ParsedXsTopoEvent {
        private XsTopoEventType eventType;
        private String timestamp;
        private String feederId;
        private String outgoingSwitchId;
        private String deviceId;
        private String measureId;
        private String switchStatus;
        private String description;
        private String completedCount;
        private String targetFeederId;

        static ParsedXsTopoEvent start(String timestamp, String feederId, String outgoingSwitchId) {
            ParsedXsTopoEvent event = new ParsedXsTopoEvent();
            event.eventType = XsTopoEventType.START;
            event.timestamp = timestamp;
            event.feederId = feederId;
            event.outgoingSwitchId = outgoingSwitchId;
            return event;
        }

        static ParsedXsTopoEvent transferStart(String timestamp, String feederId, String switchId) {
            ParsedXsTopoEvent event = new ParsedXsTopoEvent();
            event.eventType = XsTopoEventType.TRANSFER_START;
            event.timestamp = timestamp;
            event.feederId = feederId;
            event.outgoingSwitchId = switchId;
            return event;
        }

        static ParsedXsTopoEvent transferSectionStart(String timestamp, String feederId, String switchId) {
            ParsedXsTopoEvent event = new ParsedXsTopoEvent();
            event.eventType = XsTopoEventType.TRANSFER_SECTION_START;
            event.timestamp = timestamp;
            event.feederId = feederId;
            event.outgoingSwitchId = switchId;
            return event;
        }

        static ParsedXsTopoEvent transferMapping(String timestamp, String sourceFeederId, String targetFeederId, String deviceId) {
            ParsedXsTopoEvent event = new ParsedXsTopoEvent();
            event.eventType = XsTopoEventType.TRANSFER_MAPPING;
            event.timestamp = timestamp;
            event.feederId = sourceFeederId;
            event.targetFeederId = targetFeederId;
            event.deviceId = deviceId;
            return event;
        }

        static ParsedXsTopoEvent transferUpdateSql(String timestamp, String feederId, String sqlText) {
            ParsedXsTopoEvent event = new ParsedXsTopoEvent();
            event.eventType = XsTopoEventType.TRANSFER_UPDATE_SQL;
            event.timestamp = timestamp;
            event.feederId = feederId;
            event.description = sqlText;
            return event;
        }

        static ParsedXsTopoEvent device(XsTopoEventType eventType,
                                        String timestamp,
                                        String feederId,
                                        String deviceId,
                                        String measureId,
                                        String switchStatus,
                                        String description) {
            ParsedXsTopoEvent event = new ParsedXsTopoEvent();
            event.eventType = eventType;
            event.timestamp = timestamp;
            event.feederId = feederId;
            event.deviceId = deviceId;
            event.measureId = measureId;
            event.switchStatus = switchStatus;
            event.description = description;
            return event;
        }

        static ParsedXsTopoEvent topologyEnd(String timestamp, String feederId, String completedCount) {
            ParsedXsTopoEvent event = new ParsedXsTopoEvent();
            event.eventType = XsTopoEventType.TOPOLOGY_END;
            event.timestamp = timestamp;
            event.feederId = feederId;
            event.completedCount = completedCount;
            return event;
        }

        static ParsedXsTopoEvent textEvent(XsTopoEventType eventType,
                                           String timestamp,
                                           String feederId,
                                           String description) {
            ParsedXsTopoEvent event = new ParsedXsTopoEvent();
            event.eventType = eventType;
            event.timestamp = timestamp;
            event.feederId = feederId;
            event.description = description;
            return event;
        }
    }

    private static class StreamingContext {
        private String currentFeederId;
        private String currentOutgoingSwitchId;
        private String transferSourceFeederId;
        private String transferSwitchId;
        private String transferTargetFeederId;
        private boolean transferModeActive;

        void clearNormalContext() {
            currentFeederId = null;
            currentOutgoingSwitchId = null;
        }

        void clearTransferContext() {
            transferSourceFeederId = null;
            transferSwitchId = null;
            transferTargetFeederId = null;
            transferModeActive = false;
        }
    }

    private static class ParseResult {
        private long totalLines;
        private long totalRows;
        private long batchCount;
        private int sheetCount;
    }

    private static class LookupContext {
        private final TopoDeviceMapper topoDeviceMapper;
        private final int lookupBatchSize;
        private final Map<String, TopoFeederRecord> feederCache = new HashMap<String, TopoFeederRecord>();
        private final Set<String> missingFeederIds = new HashSet<String>();
        private final Map<String, TopoDeviceRecord> deviceCache = new HashMap<String, TopoDeviceRecord>();
        private final Set<String> missingDeviceIds = new HashSet<String>();

        LookupContext(TopoDeviceMapper topoDeviceMapper, int lookupBatchSize) {
            this.topoDeviceMapper = topoDeviceMapper;
            this.lookupBatchSize = lookupBatchSize;
        }

        void prefetch(List<ParsedXsTopoEvent> events) {
            Set<String> feederIds = new HashSet<String>();
            Set<String> deviceIds = new HashSet<String>();

            for (ParsedXsTopoEvent event : events) {
                if (event.feederId != null && !feederCache.containsKey(event.feederId) && !missingFeederIds.contains(event.feederId)) {
                    feederIds.add(event.feederId);
                }
                if (event.targetFeederId != null && !feederCache.containsKey(event.targetFeederId) && !missingFeederIds.contains(event.targetFeederId)) {
                    feederIds.add(event.targetFeederId);
                }
                if ((event.eventType == XsTopoEventType.START
                        || event.eventType == XsTopoEventType.TRANSFER_START
                        || event.eventType == XsTopoEventType.TRANSFER_SECTION_START)
                        && event.outgoingSwitchId != null
                        && !deviceCache.containsKey(event.outgoingSwitchId) && !missingDeviceIds.contains(event.outgoingSwitchId)) {
                    deviceIds.add(event.outgoingSwitchId);
                }
                if (event.deviceId != null && !deviceCache.containsKey(event.deviceId) && !missingDeviceIds.contains(event.deviceId)) {
                    deviceIds.add(event.deviceId);
                }
            }

            fetchFeeders(feederIds);
            fetchDevices(deviceIds);
        }

        TopoFeederRecord getCachedFeeder(String feederId) {
            return feederId == null ? null : feederCache.get(feederId);
        }

        TopoDeviceRecord getCachedDevice(String deviceId) {
            return deviceId == null ? null : deviceCache.get(deviceId);
        }

        private void fetchFeeders(Set<String> feederIds) {
            if (feederIds.isEmpty()) {
                return;
            }

            List<String> pending = new ArrayList<String>(feederIds);
            for (int i = 0; i < pending.size(); i += lookupBatchSize) {
                int end = Math.min(i + lookupBatchSize, pending.size());
                List<String> batch = pending.subList(i, end);
                List<TopoFeederRecord> records = topoDeviceMapper.selectFeederInfoByIds(batch);
                Set<String> hits = new HashSet<String>();
                if (records != null) {
                    for (TopoFeederRecord record : records) {
                        feederCache.put(record.getId(), record);
                        hits.add(record.getId());
                    }
                }
                for (String feederId : batch) {
                    if (!hits.contains(feederId)) {
                        missingFeederIds.add(feederId);
                    }
                }
            }
        }

        private void fetchDevices(Set<String> deviceIds) {
            if (deviceIds.isEmpty()) {
                return;
            }

            List<String> pending = new ArrayList<String>(deviceIds);
            for (int i = 0; i < pending.size(); i += lookupBatchSize) {
                int end = Math.min(i + lookupBatchSize, pending.size());
                List<String> batch = pending.subList(i, end);
                List<TopoDeviceRecord> records = topoDeviceMapper.selectDeviceInfoByIds(batch);
                Set<String> hits = new HashSet<String>();
                if (records != null) {
                    for (TopoDeviceRecord record : records) {
                        deviceCache.put(record.getId(), record);
                        hits.add(record.getId());
                    }
                }
                for (String deviceId : batch) {
                    if (!hits.contains(deviceId)) {
                        missingDeviceIds.add(deviceId);
                    }
                }
            }
        }
    }

    private static class XsTopoExcelWriter implements AutoCloseable {
        private final Path outputFile;
        private final int writeBatchSize;
        private final int sheetRowLimit;
        private final List<XsTopoEventExportRow> buffer = new ArrayList<XsTopoEventExportRow>();
        private ExcelWriter excelWriter;
        private WriteSheet currentSheet;
        private int currentSheetIndex = 1;
        private int currentSheetRowCount = 0;

        XsTopoExcelWriter(Path outputFile, int writeBatchSize, int sheetRowLimit) {
            this.outputFile = outputFile;
            this.writeBatchSize = writeBatchSize;
            this.sheetRowLimit = sheetRowLimit;
        }

        void write(XsTopoEventExportRow row) {
            ensureSheet();
            if (currentSheetRowCount >= sheetRowLimit) {
                flush();
                currentSheetIndex++;
                currentSheetRowCount = 0;
                currentSheet = null;
                ensureSheet();
            }

            buffer.add(row);
            currentSheetRowCount++;
            if (buffer.size() >= writeBatchSize) {
                flush();
            }
        }

        int getCurrentSheetIndex() {
            return currentSheetIndex;
        }

        private void ensureSheet() {
            if (excelWriter == null) {
                excelWriter = EasyExcel.write(outputFile.toString(), XsTopoEventExportRow.class).build();
            }
            if (currentSheet == null) {
                currentSheet = EasyExcel.writerSheet(currentSheetIndex - 1, "XS拓扑信息-" + currentSheetIndex).build();
            }
        }

        private void flush() {
            if (buffer.isEmpty()) {
                return;
            }
            excelWriter.write(buffer, currentSheet);
            buffer.clear();
        }

        @Override
        public void close() {
            if (excelWriter == null) {
                return;
            }
            flush();
            excelWriter.finish();
        }
    }
}
