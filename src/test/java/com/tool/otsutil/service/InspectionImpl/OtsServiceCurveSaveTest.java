package com.tool.otsutil.service.InspectionImpl;

import com.alicloud.openservices.tablestore.SyncClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tool.otsutil.model.dto.ots.CurveTemplateType;
import com.tool.otsutil.service.ots.CurveTemplateLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class OtsServiceCurveSaveTest {

    private static final DateTimeFormatter TEMPLATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @TempDir
    Path tempDir;

    @Test
    void shouldWriteSingleDayActiveCurveAndKeepNightZero() throws Exception {
        Path templateFile = tempDir.resolve("curve.json");
        Files.write(templateFile, buildTemplateContent().getBytes(StandardCharsets.UTF_8));

        Method saveCurveData = OtsService.class.getMethod("saveCurveData", String.class, CurveTemplateType.class, String.class);
        CurveTemplateLoader loader = new CurveTemplateLoader(new ObjectMapper(), templateFile.toString());
        OtsService otsService = spy(new OtsService(mock(SyncClient.class)));
        ReflectionTestUtils.setField(otsService, "curveTemplateLoader", loader);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<BigDecimal> valueCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<String> timeCaptor = ArgumentCaptor.forClass(String.class);
        doNothing().when(otsService).writeData(keyCaptor.capture(), valueCaptor.capture(), timeCaptor.capture());

        Object result = saveCurveData.invoke(otsService, "123456", CurveTemplateType.P, "20260423");

        assertEquals("20260423", invokeValue(result, "getDate").toString());
        assertEquals(96, ((Number) invokeValue(result, "getWrittenCount")).intValue());

        LocalDate templateDate = LocalDate.parse(invokeValue(result, "getTemplateDate").toString());
        Map<LocalDate, List<BigDecimal>> activeTemplates = buildActiveTemplates();
        List<BigDecimal> chosenTemplate = activeTemplates.get(templateDate);

        assertEquals(96, keyCaptor.getAllValues().size());
        assertEquals("12345620260423000000", keyCaptor.getAllValues().get(0));
        assertEquals("12345620260423234500", keyCaptor.getAllValues().get(95));
        assertEquals("2026-04-23 00:00:00", timeCaptor.getAllValues().get(0));
        assertEquals("2026-04-23 23:45:00", timeCaptor.getAllValues().get(95));

        for (int index : Arrays.asList(0, 12, 80, 95)) {
            assertEquals(0, valueCaptor.getAllValues().get(index).compareTo(BigDecimal.ZERO));
        }

        assertValueWithinRange(valueCaptor.getAllValues().get(24), chosenTemplate.get(24));
        assertValueWithinRange(valueCaptor.getAllValues().get(25), chosenTemplate.get(25));
        assertValueWithinRange(valueCaptor.getAllValues().get(50), chosenTemplate.get(50));
    }

    @Test
    void shouldWriteReactiveCurveForSingleDay() throws Exception {
        Path templateFile = tempDir.resolve("curve.json");
        Files.write(templateFile, buildTemplateContent().getBytes(StandardCharsets.UTF_8));

        Method saveCurveData = OtsService.class.getMethod("saveCurveData", String.class, CurveTemplateType.class, String.class);
        CurveTemplateLoader loader = new CurveTemplateLoader(new ObjectMapper(), templateFile.toString());
        OtsService otsService = spy(new OtsService(mock(SyncClient.class)));
        ReflectionTestUtils.setField(otsService, "curveTemplateLoader", loader);

        ArgumentCaptor<BigDecimal> valueCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        doNothing().when(otsService).writeData(anyString(), valueCaptor.capture(), anyString());

        Object result = saveCurveData.invoke(otsService, "654321", CurveTemplateType.Q, "20260424");

        assertEquals(96, ((Number) invokeValue(result, "getWrittenCount")).intValue());
        assertTrue(Arrays.asList("2026-04-21", "2026-04-22").contains(invokeValue(result, "getTemplateDate").toString()));
        assertTrue(valueCaptor.getAllValues().stream().anyMatch(value -> value.compareTo(BigDecimal.ZERO) > 0));
    }

    private Object invokeValue(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private void assertValueWithinRange(BigDecimal actual, BigDecimal source) {
        BigDecimal lowerBound = source.multiply(new BigDecimal("0.95"));
        BigDecimal upperBound = source.multiply(new BigDecimal("1.05"));
        assertTrue(actual.compareTo(lowerBound) >= 0, "actual value should not be lower than -5%");
        assertTrue(actual.compareTo(upperBound) <= 0, "actual value should not be higher than +5%");
        assertEquals(source.scale(), actual.scale());
    }

    private String buildTemplateContent() {
        return buildSingleDocument("2026-04-22", buildActiveTemplates().get(LocalDate.parse("2026-04-22")), buildReactiveTemplates().get(LocalDate.parse("2026-04-22")))
                + System.lineSeparator()
                + System.lineSeparator()
                + buildSingleDocument("2026-04-21", buildActiveTemplates().get(LocalDate.parse("2026-04-21")), buildReactiveTemplates().get(LocalDate.parse("2026-04-21")));
    }

    private String buildSingleDocument(String date, List<BigDecimal> activeValues, List<BigDecimal> reactiveValues) {
        return "{\n" +
                "  \"success\": true,\n" +
                "  \"message\": \"成功\",\n" +
                "  \"dataMap\": {},\n" +
                "  \"data\": {\n" +
                "    \"有功P\": {\n" +
                "      \"measValues\": " + buildValueArray(activeValues) + ",\n" +
                "      \"dataTimes\": " + buildTimes(date) + "\n" +
                "    },\n" +
                "    \"无功Q\": {\n" +
                "      \"measValues\": " + buildValueArray(reactiveValues) + ",\n" +
                "      \"dataTimes\": " + buildTimes(date) + "\n" +
                "    }\n" +
                "  },\n" +
                "  \"code\": 200\n" +
                "}";
    }

    private String buildValueArray(List<BigDecimal> values) {
        return values.stream()
                .map(value -> "\"" + value.toPlainString() + "\"")
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private String buildTimes(String date) {
        LocalDate day = LocalDate.parse(date);
        return IntStream.range(0, 96)
                .mapToObj(index -> "\"" + TEMPLATE_TIME_FORMAT.format(day.atStartOfDay().plusMinutes(index * 15L)) + "\"")
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private Map<LocalDate, List<BigDecimal>> buildActiveTemplates() {
        Map<LocalDate, List<BigDecimal>> templates = new LinkedHashMap<LocalDate, List<BigDecimal>>();
        templates.put(LocalDate.parse("2026-04-21"), buildSeries("8.4000", "10.6000", "6.2500"));
        templates.put(LocalDate.parse("2026-04-22"), buildSeries("12.3000", "15.5000", "9.8750"));
        return templates;
    }

    private Map<LocalDate, List<BigDecimal>> buildReactiveTemplates() {
        Map<LocalDate, List<BigDecimal>> templates = new LinkedHashMap<LocalDate, List<BigDecimal>>();
        templates.put(LocalDate.parse("2026-04-21"), buildSeries("0.0800", "0.1100", "0.0620"));
        templates.put(LocalDate.parse("2026-04-22"), buildSeries("0.1200", "0.1500", "0.0940"));
        return templates;
    }

    private List<BigDecimal> buildSeries(String firstPeak, String secondPeak, String thirdPeak) {
        return IntStream.range(0, 96)
                .mapToObj(index -> {
                    if (index == 24) {
                        return new BigDecimal(firstPeak);
                    }
                    if (index == 25) {
                        return new BigDecimal(secondPeak);
                    }
                    if (index == 50) {
                        return new BigDecimal(thirdPeak);
                    }
                    return BigDecimal.ZERO.setScale(0);
                })
                .collect(Collectors.toList());
    }
}
