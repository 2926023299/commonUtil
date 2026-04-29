package com.tool.otsutil.mysqlworkbench.util;

import com.tool.otsutil.exception.CustomException;
import com.tool.otsutil.model.common.AppHttpCodeEnum;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlDesignColumnRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlDesignIndexRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlTableDesignRequest;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlColumnView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlDesignPreviewView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlIndexView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlTableMetadataView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class MysqlTableDesignSqlBuilder {

    private MysqlTableDesignSqlBuilder() {
    }

    public static MysqlDesignPreviewView buildPreview(MysqlTableDesignRequest request,
                                                      MysqlTableMetadataView currentMetadata,
                                                      boolean tableExists) {
        validateRequest(request);
        MysqlDesignPreviewView previewView = new MysqlDesignPreviewView();
        previewView.setSchema(request.getSchema());
        previewView.setTable(request.getTable());
        previewView.setCreateMode(Boolean.TRUE.equals(request.getCreateMode()) || !tableExists);
        previewView.setDangerous(Boolean.TRUE);

        List<String> statements = tableExists && !Boolean.TRUE.equals(request.getCreateMode())
                ? buildAlterStatements(request, currentMetadata)
                : Collections.singletonList(buildCreateTableStatement(request));
        previewView.setStatements(statements);
        return previewView;
    }

    private static void validateRequest(MysqlTableDesignRequest request) {
        MysqlIdentifierUtils.validateIdentifier(request.getSchema(), "schema 名称不合法");
        MysqlIdentifierUtils.validateIdentifier(request.getTable(), "表名不合法");
        if (request.getColumns() == null || request.getColumns().isEmpty()) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "至少需要一个字段");
        }
        for (MysqlDesignColumnRequest column : request.getColumns()) {
            MysqlIdentifierUtils.validateIdentifier(column.getName(), "字段名不合法");
            if (column.getType() == null || column.getType().trim().isEmpty()) {
                throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "字段类型不能为空");
            }
        }
        if (request.getIndexes() != null) {
            for (MysqlDesignIndexRequest index : request.getIndexes()) {
                MysqlIdentifierUtils.validateIdentifier(index.getName(), "索引名不合法");
                MysqlIdentifierUtils.validateIdentifiers(index.getColumns(), "索引字段不合法");
            }
        }
    }

    private static String buildCreateTableStatement(MysqlTableDesignRequest request) {
        List<String> lines = new ArrayList<String>();
        List<String> primaryColumns = new ArrayList<String>();

        for (MysqlDesignColumnRequest column : request.getColumns()) {
            lines.add("  " + buildColumnDefinition(column));
            if (Boolean.TRUE.equals(column.getPrimaryKey())) {
                primaryColumns.add(MysqlIdentifierUtils.quoteIdentifier(column.getName()));
            }
        }

        if (!primaryColumns.isEmpty()) {
            lines.add("  PRIMARY KEY (" + String.join(", ", primaryColumns) + ")");
        }

        for (MysqlDesignIndexRequest index : normalizeIndexes(request.getIndexes())) {
            lines.add("  " + buildIndexDefinition(index));
        }

        String engine = request.getEngine() == null || request.getEngine().trim().isEmpty() ? "InnoDB" : request.getEngine().trim();
        String charset = request.getCharset() == null || request.getCharset().trim().isEmpty() ? "utf8mb4" : request.getCharset().trim();
        StringBuilder builder = new StringBuilder();
        builder.append("CREATE TABLE ")
                .append(MysqlIdentifierUtils.qualifyTable(request.getSchema(), request.getTable()))
                .append(" (\n")
                .append(String.join(",\n", lines))
                .append("\n) ENGINE=")
                .append(engine)
                .append(" DEFAULT CHARSET=")
                .append(charset);
        if (request.getTableComment() != null && !request.getTableComment().trim().isEmpty()) {
            builder.append(" COMMENT='").append(escapeSingleQuote(request.getTableComment().trim())).append("'");
        }
        return builder.toString();
    }

    private static List<String> buildAlterStatements(MysqlTableDesignRequest request, MysqlTableMetadataView currentMetadata) {
        List<String> statements = new ArrayList<String>();
        Map<String, MysqlColumnView> currentColumns = currentMetadata.getColumns().stream()
                .collect(Collectors.toMap(MysqlColumnView::getName, column -> column, (left, right) -> left, LinkedHashMap::new));
        Set<String> targetColumnNames = new LinkedHashSet<String>();

        for (MysqlDesignColumnRequest column : request.getColumns()) {
            targetColumnNames.add(column.getName());
            MysqlColumnView currentColumn = currentColumns.get(column.getName());
            if (currentColumn == null) {
                statements.add("ALTER TABLE " + MysqlIdentifierUtils.qualifyTable(request.getSchema(), request.getTable())
                        + " ADD COLUMN " + buildColumnDefinition(column));
                continue;
            }

            if (!buildColumnSignature(column).equals(buildColumnSignature(currentColumn))) {
                statements.add("ALTER TABLE " + MysqlIdentifierUtils.qualifyTable(request.getSchema(), request.getTable())
                        + " MODIFY COLUMN " + buildColumnDefinition(column));
            }
        }

        for (MysqlColumnView currentColumn : currentMetadata.getColumns()) {
            if (!targetColumnNames.contains(currentColumn.getName())) {
                statements.add("ALTER TABLE " + MysqlIdentifierUtils.qualifyTable(request.getSchema(), request.getTable())
                        + " DROP COLUMN " + MysqlIdentifierUtils.quoteIdentifier(currentColumn.getName()));
            }
        }

        List<String> currentPrimary = currentMetadata.getIndexes().stream()
                .filter(index -> Boolean.TRUE.equals(index.getPrimaryKey()))
                .findFirst()
                .map(MysqlIndexView::getColumns)
                .orElse(Collections.<String>emptyList());
        List<String> targetPrimary = request.getColumns().stream()
                .filter(column -> Boolean.TRUE.equals(column.getPrimaryKey()))
                .map(MysqlDesignColumnRequest::getName)
                .collect(Collectors.toList());
        if (!currentPrimary.equals(targetPrimary)) {
            if (!currentPrimary.isEmpty()) {
                statements.add("ALTER TABLE " + MysqlIdentifierUtils.qualifyTable(request.getSchema(), request.getTable()) + " DROP PRIMARY KEY");
            }
            if (!targetPrimary.isEmpty()) {
                statements.add("ALTER TABLE " + MysqlIdentifierUtils.qualifyTable(request.getSchema(), request.getTable())
                        + " ADD PRIMARY KEY (" + quoteColumns(targetPrimary) + ")");
            }
        }

        Map<String, MysqlIndexView> currentIndexes = currentMetadata.getIndexes().stream()
                .filter(index -> !Boolean.TRUE.equals(index.getPrimaryKey()))
                .collect(Collectors.toMap(MysqlIndexView::getName, index -> index, (left, right) -> left, LinkedHashMap::new));
        Map<String, MysqlDesignIndexRequest> targetIndexes = normalizeIndexes(request.getIndexes()).stream()
                .collect(Collectors.toMap(MysqlDesignIndexRequest::getName, index -> index, (left, right) -> left, LinkedHashMap::new));

        for (MysqlIndexView currentIndex : currentIndexes.values()) {
            MysqlDesignIndexRequest targetIndex = targetIndexes.get(currentIndex.getName());
            if (targetIndex == null || !buildIndexSignature(currentIndex).equals(buildIndexSignature(targetIndex))) {
                statements.add("ALTER TABLE " + MysqlIdentifierUtils.qualifyTable(request.getSchema(), request.getTable())
                        + " DROP INDEX " + MysqlIdentifierUtils.quoteIdentifier(currentIndex.getName()));
            }
        }

        for (MysqlDesignIndexRequest targetIndex : targetIndexes.values()) {
            MysqlIndexView currentIndex = currentIndexes.get(targetIndex.getName());
            if (currentIndex == null || !buildIndexSignature(currentIndex).equals(buildIndexSignature(targetIndex))) {
                statements.add("ALTER TABLE " + MysqlIdentifierUtils.qualifyTable(request.getSchema(), request.getTable())
                        + " ADD " + buildIndexDefinition(targetIndex));
            }
        }

        return statements;
    }

    private static List<MysqlDesignIndexRequest> normalizeIndexes(List<MysqlDesignIndexRequest> indexes) {
        if (indexes == null) {
            return Collections.emptyList();
        }
        return indexes.stream()
                .filter(index -> index.getColumns() != null && !index.getColumns().isEmpty())
                .collect(Collectors.toList());
    }

    private static String buildColumnSignature(MysqlDesignColumnRequest column) {
        return column.getName() + "|" + buildColumnDefinition(column);
    }

    private static String buildColumnSignature(MysqlColumnView column) {
        StringBuilder builder = new StringBuilder();
        builder.append(column.getName()).append("|")
                .append(column.getColumnType()).append("|")
                .append(Boolean.TRUE.equals(column.getNullable()) ? "NULL" : "NOT_NULL").append("|")
                .append(column.getDefaultValue()).append("|")
                .append(Boolean.TRUE.equals(column.getAutoIncrement())).append("|")
                .append(column.getComment());
        return builder.toString();
    }

    private static String buildIndexSignature(MysqlDesignIndexRequest index) {
        return index.getName() + "|" + Boolean.TRUE.equals(index.getUnique()) + "|" + String.join(",", index.getColumns());
    }

    private static String buildIndexSignature(MysqlIndexView index) {
        return index.getName() + "|" + Boolean.TRUE.equals(index.getUnique()) + "|" + String.join(",", index.getColumns());
    }

    private static String buildIndexDefinition(MysqlDesignIndexRequest index) {
        StringBuilder builder = new StringBuilder();
        if (Boolean.TRUE.equals(index.getUnique())) {
            builder.append("UNIQUE ");
        }
        builder.append("INDEX ")
                .append(MysqlIdentifierUtils.quoteIdentifier(index.getName()))
                .append(" (")
                .append(quoteColumns(index.getColumns()))
                .append(")");
        return builder.toString();
    }

    private static String quoteColumns(List<String> columns) {
        return columns.stream().map(MysqlIdentifierUtils::quoteIdentifier).collect(Collectors.joining(", "));
    }

    private static String buildColumnDefinition(MysqlDesignColumnRequest column) {
        StringBuilder builder = new StringBuilder();
        builder.append(MysqlIdentifierUtils.quoteIdentifier(column.getName())).append(" ").append(buildColumnType(column));
        builder.append(Boolean.TRUE.equals(column.getNullable()) ? " NULL" : " NOT NULL");
        if (column.getDefaultValue() != null && !column.getDefaultValue().trim().isEmpty()) {
            builder.append(" DEFAULT ").append(formatDefaultValue(column.getDefaultValue(), column.getType()));
        }
        if (Boolean.TRUE.equals(column.getAutoIncrement())) {
            builder.append(" AUTO_INCREMENT");
        }
        if (column.getComment() != null && !column.getComment().trim().isEmpty()) {
            builder.append(" COMMENT '").append(escapeSingleQuote(column.getComment().trim())).append("'");
        }
        return builder.toString();
    }

    private static String buildColumnType(MysqlDesignColumnRequest column) {
        String type = column.getType().trim().toUpperCase(Locale.ROOT);
        if (column.getLength() == null) {
            return type;
        }
        if (column.getScale() == null) {
            return type + "(" + column.getLength() + ")";
        }
        return type + "(" + column.getLength() + "," + column.getScale() + ")";
    }

    private static String formatDefaultValue(String defaultValue, String dataType) {
        String trimmed = defaultValue.trim();
        if ("NULL".equalsIgnoreCase(trimmed) || trimmed.toUpperCase(Locale.ROOT).startsWith("CURRENT_")) {
            return trimmed;
        }
        String normalizedType = dataType == null ? "" : dataType.toUpperCase(Locale.ROOT);
        if (normalizedType.contains("INT") || normalizedType.contains("DECIMAL") || normalizedType.contains("DOUBLE")
                || normalizedType.contains("FLOAT") || normalizedType.contains("NUMERIC") || normalizedType.contains("BIT")) {
            return trimmed;
        }
        return "'" + escapeSingleQuote(trimmed) + "'";
    }

    private static String escapeSingleQuote(String value) {
        return value.replace("'", "''");
    }
}
