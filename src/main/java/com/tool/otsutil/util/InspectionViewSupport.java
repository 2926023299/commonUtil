package com.tool.otsutil.util;

import com.tool.otsutil.model.vo.inspection.JavaProcessDiffView;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class InspectionViewSupport {

    private static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter[] INPUT_FORMATTERS = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern("yyyy-M-d H:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    };

    private InspectionViewSupport() {
    }

    public static String formatDateTime(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.format(OUTPUT_FORMATTER);
    }

    public static LocalDateTime parseDateTime(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return null;
        }

        String normalized = rawValue.trim().replace("T", " ");
        for (DateTimeFormatter formatter : INPUT_FORMATTERS) {
            try {
                if (formatter == DateTimeFormatter.ISO_LOCAL_DATE_TIME) {
                    return LocalDateTime.parse(rawValue.trim(), formatter);
                }
                return LocalDateTime.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        throw new IllegalArgumentException("Unsupported datetime format: " + rawValue);
    }

    public static List<String> splitJavaProcesses(String rawValue) {
        List<String> processes = new ArrayList<String>();
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return processes;
        }

        String[] segments = rawValue.split("\\r?\\n");
        Set<String> unique = new LinkedHashSet<String>();
        for (String segment : segments) {
            String value = segment == null ? "" : segment.trim();
            if (!value.isEmpty()) {
                unique.add(value);
            }
        }
        processes.addAll(unique);
        return processes;
    }

    public static JavaProcessDiffView diffJavaProcesses(String currentRawValue, String previousRawValue) {
        return diffJavaProcesses(splitJavaProcesses(currentRawValue), splitJavaProcesses(previousRawValue));
    }

    public static JavaProcessDiffView diffJavaProcesses(List<String> currentProcesses, List<String> previousProcesses) {
        JavaProcessDiffView diffView = new JavaProcessDiffView();
        Set<String> currentSet = new LinkedHashSet<String>(currentProcesses);
        Set<String> previousSet = new LinkedHashSet<String>(previousProcesses);

        for (String process : currentProcesses) {
            if (!previousSet.contains(process)) {
                diffView.getAddedProcesses().add(process);
            } else {
                diffView.getUnchangedProcesses().add(process);
            }
        }

        for (String process : previousProcesses) {
            if (!currentSet.contains(process)) {
                diffView.getRemovedProcesses().add(process);
            }
        }

        return diffView;
    }

    public static double parseNumeric(String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return 0D;
        }

        String normalized = rawValue.replace("%", "").trim();
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ignored) {
            return 0D;
        }
    }
}
