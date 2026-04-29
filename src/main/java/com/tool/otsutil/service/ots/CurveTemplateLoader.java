package com.tool.otsutil.service.ots;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tool.otsutil.config.OtsProperties;
import com.tool.otsutil.exception.CustomException;
import com.tool.otsutil.model.common.AppHttpCodeEnum;
import com.tool.otsutil.model.dto.ots.CurveTemplateDay;
import com.tool.otsutil.model.dto.ots.CurveTemplateType;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class CurveTemplateLoader {

    private static final int EXPECTED_POINTS = 96;
    private static final DateTimeFormatter TEMPLATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ObjectMapper objectMapper;
    private final String curveTemplatePath;

    @Autowired
    public CurveTemplateLoader(ObjectMapper objectMapper, OtsProperties otsProperties) {
        this(objectMapper, otsProperties.getCurveTemplatePath());
    }

    public CurveTemplateLoader(ObjectMapper objectMapper, String curveTemplatePath) {
        this.objectMapper = objectMapper;
        this.curveTemplatePath = curveTemplatePath;
    }

    public Map<CurveTemplateType, List<CurveTemplateDay>> loadTemplates() {
        Path templateFile = Paths.get(curveTemplatePath);
        if (!Files.exists(templateFile)) {
            throw new CustomException(AppHttpCodeEnum.SERVER_ERROR, "曲线模板文件不存在: " + templateFile);
        }

        try {
            String content = new String(Files.readAllBytes(templateFile), StandardCharsets.UTF_8);
            MappingIterator<CurveTemplateDocument> iterator = objectMapper.readerFor(CurveTemplateDocument.class)
                    .readValues(content);

            Map<CurveTemplateType, List<CurveTemplateDay>> result = new EnumMap<CurveTemplateType, List<CurveTemplateDay>>(CurveTemplateType.class);
            for (CurveTemplateType type : CurveTemplateType.values()) {
                result.put(type, new ArrayList<CurveTemplateDay>());
            }

            while (iterator.hasNext()) {
                CurveTemplateDocument document = iterator.next();
                for (CurveTemplateType type : CurveTemplateType.values()) {
                    CurveTemplateSeries series = document.data.get(type.getTemplateName());
                    if (series == null) {
                        throw new CustomException(AppHttpCodeEnum.SERVER_ERROR, "曲线模板缺少类型: " + type.getTemplateName());
                    }
                    result.get(type).add(convertSeries(type, series));
                }
            }

            for (CurveTemplateType type : CurveTemplateType.values()) {
                result.get(type).sort((left, right) -> left.getSourceDate().compareTo(right.getSourceDate()));
                if (result.get(type).isEmpty()) {
                    throw new CustomException(AppHttpCodeEnum.SERVER_ERROR, "曲线模板为空: " + type.name());
                }
            }

            logParsedTemplates(result);

            return result;
        } catch (IOException e) {
            throw new CustomException(AppHttpCodeEnum.SERVER_ERROR, "曲线模板解析失败: " + e.getMessage());
        }
    }

    private CurveTemplateDay convertSeries(CurveTemplateType type, CurveTemplateSeries series) {
        if (series.measValues == null || series.dataTimes == null || series.measValues.size() != series.dataTimes.size()) {
            throw new CustomException(AppHttpCodeEnum.SERVER_ERROR, "曲线模板数据点数量不匹配: " + type.name());
        }
        if (series.measValues.size() != EXPECTED_POINTS) {
            throw new CustomException(AppHttpCodeEnum.SERVER_ERROR, "曲线模板数据点必须为96个: " + type.name());
        }

        List<BigDecimal> values = new ArrayList<BigDecimal>(series.measValues.size());
        List<LocalTime> times = new ArrayList<LocalTime>(series.dataTimes.size());
        LocalDate sourceDate = null;
        LocalDateTime previous = null;

        for (int index = 0; index < series.dataTimes.size(); index++) {
            LocalDateTime dateTime = LocalDateTime.parse(series.dataTimes.get(index), TEMPLATE_TIME_FORMAT);
            if (sourceDate == null) {
                sourceDate = dateTime.toLocalDate();
            } else if (!sourceDate.equals(dateTime.toLocalDate())) {
                throw new CustomException(AppHttpCodeEnum.SERVER_ERROR, "曲线模板跨天数据非法: " + type.name());
            }

            if (previous != null && Duration.between(previous, dateTime).toMinutes() != 15L) {
                throw new CustomException(AppHttpCodeEnum.SERVER_ERROR, "曲线模板时间间隔必须为15分钟: " + type.name());
            }

            values.add(new BigDecimal(series.measValues.get(index)));
            times.add(dateTime.toLocalTime());
            previous = dateTime;
        }

        return new CurveTemplateDay(sourceDate, values, times);
    }

    private void logParsedTemplates(Map<CurveTemplateType, List<CurveTemplateDay>> templates) {
        for (Map.Entry<CurveTemplateType, List<CurveTemplateDay>> entry : templates.entrySet()) {
            for (CurveTemplateDay day : entry.getValue()) {
                log.info(
                        "曲线模板解析完成: type={}, date={}, pointCount={}, times={}, values={}",
                        entry.getKey().name(),
                        day.getSourceDate(),
                        day.getValues().size(),
                        day.getTimes(),
                        day.getValues()
                );
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class CurveTemplateDocument {
        public Map<String, CurveTemplateSeries> data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class CurveTemplateSeries {
        public List<String> measValues;
        public List<String> dataTimes;
    }
}
