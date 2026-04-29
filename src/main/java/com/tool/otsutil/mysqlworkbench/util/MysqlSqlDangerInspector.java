package com.tool.otsutil.mysqlworkbench.util;

import lombok.Getter;

import java.util.Locale;
import java.util.regex.Pattern;

public final class MysqlSqlDangerInspector {

    private static final Pattern WHERE_PATTERN = Pattern.compile("\\bwhere\\b", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private MysqlSqlDangerInspector() {
    }

    public static InspectionResult inspect(String sql) {
        String statement = sql == null ? "" : sql.trim();
        String normalized = normalizeLeadingStatement(statement);
        String firstKeyword = extractFirstKeyword(normalized);
        boolean dangerous = isDangerous(firstKeyword, normalized);
        return new InspectionResult(firstKeyword, dangerous, dangerous ? buildReason(firstKeyword) : "");
    }

    private static String normalizeLeadingStatement(String sql) {
        String normalized = sql == null ? "" : sql.trim();
        while (normalized.startsWith("/*")) {
            int endIndex = normalized.indexOf("*/");
            if (endIndex < 0) {
                return "";
            }
            normalized = normalized.substring(endIndex + 2).trim();
        }
        return normalized;
    }

    private static String extractFirstKeyword(String sql) {
        if (sql == null || sql.isEmpty()) {
            return "";
        }
        int separator = sql.indexOf(' ');
        String keyword = separator >= 0 ? sql.substring(0, separator) : sql;
        return keyword.toUpperCase(Locale.ROOT);
    }

    private static boolean isDangerous(String keyword, String normalizedSql) {
        if ("DROP".equals(keyword) || "TRUNCATE".equals(keyword) || "ALTER".equals(keyword)
                || "CREATE".equals(keyword) || "RENAME".equals(keyword)) {
            return true;
        }
        if ("UPDATE".equals(keyword)) {
            return !WHERE_PATTERN.matcher(normalizedSql).find();
        }
        if ("DELETE".equals(keyword)) {
            return !WHERE_PATTERN.matcher(normalizedSql).find();
        }
        return false;
    }

    private static String buildReason(String keyword) {
        if ("UPDATE".equals(keyword) || "DELETE".equals(keyword)) {
            return "检测到缺少 WHERE 条件的写操作";
        }
        return "检测到 DDL 或高风险语句";
    }

    @Getter
    public static class InspectionResult {

        private final String statementType;

        private final boolean dangerous;

        private final String reason;

        public InspectionResult(String statementType, boolean dangerous, String reason) {
            this.statementType = statementType;
            this.dangerous = dangerous;
            this.reason = reason;
        }
    }
}
