package com.tool.otsutil.service;

import com.tool.otsutil.model.dto.tools.TimestampConvertRequest;
import com.tool.otsutil.model.vo.tools.TimestampConvertResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class ToolsService {

    private static final DateTimeFormatter LOCAL_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Map<String, Object> currentTimestamp() {
        long millis = System.currentTimeMillis();
        Map<String, Object> result = new HashMap<>();
        result.put("seconds", millis / 1000);
        result.put("millis", millis);
        return result;
    }

    public TimestampConvertResult convertTimestamp(TimestampConvertRequest request) {
        String direction = request.getDirection();
        if ("toDate".equals(direction)) {
            return timestampToDate(request);
        } else if ("toTimestamp".equals(direction)) {
            return dateToTimestamp(request);
        }
        throw new IllegalArgumentException("无效的转换方向: " + direction);
    }

    private TimestampConvertResult timestampToDate(TimestampConvertRequest request) {
        long ts = Long.parseLong(request.getValue());
        String unit = request.getUnit();
        long millis = "s".equals(unit) ? ts * 1000 : ts;

        Instant instant = Instant.ofEpochMilli(millis);
        LocalDateTime ldt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());

        TimestampConvertResult result = new TimestampConvertResult();
        result.setTimestampSeconds(millis / 1000);
        result.setTimestampMillis(millis);
        result.setIso8601(instant.toString());
        result.setLocalDateTime(ldt.format(LOCAL_FORMATTER));
        result.setFormatted(ldt.format(LOCAL_FORMATTER));
        return result;
    }

    private TimestampConvertResult dateToTimestamp(TimestampConvertRequest request) {
        LocalDateTime ldt = LocalDateTime.parse(request.getValue(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        long millis = ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        TimestampConvertResult result = new TimestampConvertResult();
        result.setTimestampSeconds(millis / 1000);
        result.setTimestampMillis(millis);
        result.setIso8601(Instant.ofEpochMilli(millis).toString());
        result.setLocalDateTime(ldt.format(LOCAL_FORMATTER));
        result.setFormatted(ldt.format(LOCAL_FORMATTER));
        return result;
    }
}
