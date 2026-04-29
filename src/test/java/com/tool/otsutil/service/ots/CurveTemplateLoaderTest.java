package com.tool.otsutil.service.ots;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tool.otsutil.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class CurveTemplateLoaderTest {

    private static final DateTimeFormatter TEMPLATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @TempDir
    Path tempDir;

    @Test
    void shouldParseTwoConcatenatedTemplateDocuments() throws Exception {
        Path templateFile = tempDir.resolve("curve.json");
        Files.write(templateFile, buildTemplateContent(96));

        Object loader = newLoader(templateFile);
        Method loadTemplates = loader.getClass().getMethod("loadTemplates");

        @SuppressWarnings("unchecked")
        Map<Object, List<Object>> templates = (Map<Object, List<Object>>) loadTemplates.invoke(loader);

        assertEquals(2, templates.size());

        List<Object> activeTemplates = getTemplateDays(templates, "P");
        List<Object> reactiveTemplates = getTemplateDays(templates, "Q");

        assertEquals(2, activeTemplates.size());
        assertEquals(2, reactiveTemplates.size());
        assertEquals(LocalDate.parse("2026-04-21"), invokeLocalDate(activeTemplates.get(0), "getSourceDate"));
        assertEquals(LocalDate.parse("2026-04-22"), invokeLocalDate(activeTemplates.get(1), "getSourceDate"));
        assertEquals(96, invokeList(activeTemplates.get(0), "getValues").size());
        assertEquals(96, invokeList(reactiveTemplates.get(1), "getTimes").size());
    }

    @Test
    void shouldRejectTemplateWhenPointCountIsNotNinetySix() throws Exception {
        Path templateFile = tempDir.resolve("curve-invalid.json");
        Files.write(templateFile, buildTemplateContent(95));

        Object loader = newLoader(templateFile);
        Method loadTemplates = loader.getClass().getMethod("loadTemplates");

        try {
            loadTemplates.invoke(loader);
            fail("Expected invalid template point count to be rejected");
        } catch (InvocationTargetException ex) {
            assertTrue(ex.getCause() instanceof CustomException);
            assertTrue(ex.getCause().getMessage().contains("96"));
        }
    }

    @Test
    void shouldLogParsedTemplateContentAfterLoading() throws Exception {
        Path templateFile = tempDir.resolve("curve-log.json");
        Files.write(templateFile, buildTemplateContent(96));

        Object loader = newLoader(templateFile);
        Method loadTemplates = loader.getClass().getMethod("loadTemplates");

        Logger logger = (Logger) LoggerFactory.getLogger(CurveTemplateLoader.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<ILoggingEvent>();
        listAppender.start();
        logger.addAppender(listAppender);

        try {
            loadTemplates.invoke(loader);
        } finally {
            logger.detachAppender(listAppender);
            listAppender.stop();
        }

        List<String> messages = listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.toList());

        assertTrue(messages.stream().anyMatch(message -> message.contains("曲线模板解析完成")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("type=P")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("type=Q")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("date=2026-04-21")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("values=[")));
        assertTrue(messages.stream().anyMatch(message -> message.contains("times=[")));
    }

    private Object newLoader(Path templateFile) throws Exception {
        Class<?> loaderClass = Class.forName("com.tool.otsutil.service.ots.CurveTemplateLoader");
        return loaderClass.getConstructor(ObjectMapper.class, String.class)
                .newInstance(new ObjectMapper(), templateFile.toString());
    }

    private List<Object> getTemplateDays(Map<Object, List<Object>> templates, String typeName) {
        for (Map.Entry<Object, List<Object>> entry : templates.entrySet()) {
            if (typeName.equals(entry.getKey().toString())) {
                return entry.getValue();
            }
        }
        fail("Template type not found: " + typeName);
        return null;
    }

    private LocalDate invokeLocalDate(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return (LocalDate) method.invoke(target);
    }

    @SuppressWarnings("unchecked")
    private List<Object> invokeList(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        return (List<Object>) method.invoke(target);
    }

    private byte[] buildTemplateContent(int pointsPerSeries) {
        String firstDay = buildSingleDocument("2026-04-22", pointsPerSeries, "0.1000", "0.0100");
        String secondDay = buildSingleDocument("2026-04-21", pointsPerSeries, "0.2000", "0.0200");
        return (firstDay + System.lineSeparator() + System.lineSeparator() + secondDay).getBytes(StandardCharsets.UTF_8);
    }

    private String buildSingleDocument(String date, int pointsPerSeries, String activeBase, String reactiveBase) {
        return "{\n" +
                "  \"success\": true,\n" +
                "  \"message\": \"成功\",\n" +
                "  \"dataMap\": {},\n" +
                "  \"data\": {\n" +
                "    \"有功P\": {\n" +
                "      \"measValues\": " + buildValues(pointsPerSeries, activeBase) + ",\n" +
                "      \"dataTimes\": " + buildTimes(date, pointsPerSeries) + "\n" +
                "    },\n" +
                "    \"无功Q\": {\n" +
                "      \"measValues\": " + buildValues(pointsPerSeries, reactiveBase) + ",\n" +
                "      \"dataTimes\": " + buildTimes(date, pointsPerSeries) + "\n" +
                "    }\n" +
                "  },\n" +
                "  \"code\": 200\n" +
                "}";
    }

    private String buildValues(int pointsPerSeries, String baseValue) {
        return IntStream.range(0, pointsPerSeries)
                .mapToObj(index -> {
                    if (index < 24 || index > 72) {
                        return "\"0\"";
                    }
                    return "\"" + baseValue + "\"";
                })
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private String buildTimes(String date, int pointsPerSeries) {
        LocalDate day = LocalDate.parse(date);
        return IntStream.range(0, pointsPerSeries)
                .mapToObj(index -> {
                    LocalDateTime dateTime = day.atStartOfDay().plusMinutes(index * 15L);
                    return "\"" + TEMPLATE_TIME_FORMAT.format(dateTime) + "\"";
                })
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
