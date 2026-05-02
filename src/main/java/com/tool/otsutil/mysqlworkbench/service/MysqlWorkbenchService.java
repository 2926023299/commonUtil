package com.tool.otsutil.mysqlworkbench.service;

import com.tool.otsutil.exception.CustomException;
import com.tool.otsutil.model.common.AppHttpCodeEnum;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlDesignColumnRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlDesignIndexRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlHistoryQueryRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlRowDeleteRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlRowInsertRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlRowUpdateRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlSqlExecuteRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlTableDesignRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlTableFilterRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlTableQueryRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlTableSortRequest;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlColumnView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlDesignPreviewView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlHistoryDetailView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlHistoryPageView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlIndexView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlSqlErrorView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlSqlBatchResultView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlSqlStatementResultView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlTableDataPageView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlTableMetadataView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlTreeNodeView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlWriteResultView;
import com.tool.otsutil.mysqlworkbench.util.MysqlIdentifierUtils;
import com.tool.otsutil.mysqlworkbench.util.MysqlSqlErrorFormatter;
import com.tool.otsutil.mysqlworkbench.util.MysqlSqlDangerInspector;
import com.tool.otsutil.mysqlworkbench.util.MysqlTableDesignSqlBuilder;
import com.tool.otsutil.mysqlworkbench.util.SqlStatementSplitter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MysqlWorkbenchService {

    private static final List<String> SYSTEM_SCHEMAS = Arrays.asList("information_schema", "mysql", "performance_schema", "sys");

    private final JdbcTemplate jdbcTemplate;

    private final MysqlHistoryService mysqlHistoryService;

    public MysqlWorkbenchService(JdbcTemplate jdbcTemplate, MysqlHistoryService mysqlHistoryService) {
        this.jdbcTemplate = jdbcTemplate;
        this.mysqlHistoryService = mysqlHistoryService;
    }

    public List<MysqlTreeNodeView> listTree(boolean includeSystemSchemas) {
        List<String> schemas = jdbcTemplate.queryForList(
                "SELECT schema_name FROM information_schema.schemata ORDER BY schema_name",
                String.class
        );
        List<MysqlTreeNodeView> nodes = new ArrayList<MysqlTreeNodeView>();
        for (String schema : schemas) {
            if (!includeSystemSchemas && SYSTEM_SCHEMAS.contains(schema)) {
                continue;
            }
            MysqlTreeNodeView schemaNode = new MysqlTreeNodeView();
            schemaNode.setKey("schema:" + schema);
            schemaNode.setLabel(schema);
            schemaNode.setType("schema");
            List<String> tables = jdbcTemplate.queryForList(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_type = 'BASE TABLE' ORDER BY table_name",
                    String.class,
                    schema
            );
            for (String table : tables) {
                MysqlTreeNodeView tableNode = new MysqlTreeNodeView();
                tableNode.setKey("table:" + schema + "." + table);
                tableNode.setLabel(table);
                tableNode.setType("table");
                schemaNode.getChildren().add(tableNode);
            }
            nodes.add(schemaNode);
        }
        return nodes;
    }

    public MysqlTableMetadataView getTableMetadata(String schema, String table) {
        MysqlIdentifierUtils.validateIdentifier(schema, "schema 名称不合法");
        MysqlIdentifierUtils.validateIdentifier(table, "表名不合法");
        if (!tableExists(schema, table)) {
            throw new CustomException(AppHttpCodeEnum.DATA_NOT_EXIST, "表不存在: " + schema + "." + table);
        }

        Map<String, Object> tableInfo = jdbcTemplate.queryForMap(
                "SELECT engine, table_collation, table_comment FROM information_schema.tables WHERE table_schema = ? AND table_name = ? AND table_type = 'BASE TABLE'",
                schema,
                table
        );

        MysqlTableMetadataView metadataView = new MysqlTableMetadataView();
        metadataView.setSchema(schema);
        metadataView.setTable(table);
        metadataView.setEngine(String.valueOf(tableInfo.get("engine")));
        String collation = tableInfo.get("table_collation") == null ? "" : String.valueOf(tableInfo.get("table_collation"));
        metadataView.setCharset(collation.contains("_") ? collation.substring(0, collation.indexOf('_')) : collation);
        metadataView.setTableComment(String.valueOf(tableInfo.get("table_comment")));

        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, column_type, character_maximum_length, numeric_precision, numeric_scale, is_nullable, column_default, column_comment, extra "
                        + "FROM information_schema.columns WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position",
                schema,
                table
        );
        List<Map<String, Object>> indexRows = jdbcTemplate.queryForList(
                "SELECT index_name, non_unique, column_name, seq_in_index FROM information_schema.statistics "
                        + "WHERE table_schema = ? AND table_name = ? ORDER BY index_name, seq_in_index",
                schema,
                table
        );

        Map<String, MysqlIndexView> indexes = new LinkedHashMap<String, MysqlIndexView>();
        for (Map<String, Object> row : indexRows) {
            String indexName = String.valueOf(row.get("index_name"));
            MysqlIndexView indexView = indexes.get(indexName);
            if (indexView == null) {
                indexView = new MysqlIndexView();
                indexView.setName(indexName);
                indexView.setPrimaryKey("PRIMARY".equalsIgnoreCase(indexName));
                indexView.setUnique(!Boolean.TRUE.equals(row.get("non_unique")) && !"1".equals(String.valueOf(row.get("non_unique"))));
                indexes.put(indexName, indexView);
            }
            indexView.getColumns().add(String.valueOf(row.get("column_name")));
        }

        List<String> uniqueSingleColumnNames = indexes.values().stream()
                .filter(index -> Boolean.TRUE.equals(index.getUnique()))
                .filter(index -> index.getColumns().size() == 1)
                .map(index -> index.getColumns().get(0))
                .collect(Collectors.toList());

        for (Map<String, Object> row : columns) {
            MysqlColumnView columnView = new MysqlColumnView();
            columnView.setName(String.valueOf(row.get("column_name")));
            columnView.setDataType(String.valueOf(row.get("data_type")));
            columnView.setColumnType(String.valueOf(row.get("column_type")));
            columnView.setLength(asInteger(row.get("character_maximum_length"), row.get("numeric_precision")));
            columnView.setScale(asInteger(row.get("numeric_scale")));
            columnView.setNullable("YES".equalsIgnoreCase(String.valueOf(row.get("is_nullable"))));
            columnView.setDefaultValue(row.get("column_default") == null ? null : String.valueOf(row.get("column_default")));
            columnView.setComment(row.get("column_comment") == null ? "" : String.valueOf(row.get("column_comment")));
            String extra = row.get("extra") == null ? "" : String.valueOf(row.get("extra")).toLowerCase(Locale.ROOT);
            columnView.setAutoIncrement(extra.contains("auto_increment"));
            columnView.setPrimaryKey(indexes.containsKey("PRIMARY") && indexes.get("PRIMARY").getColumns().contains(columnView.getName()));
            columnView.setUniqueKey(uniqueSingleColumnNames.contains(columnView.getName()));
            metadataView.getColumns().add(columnView);
        }

        metadataView.setIndexes(new ArrayList<MysqlIndexView>(indexes.values()));
        List<String> primaryColumns = indexes.containsKey("PRIMARY")
                ? indexes.get("PRIMARY").getColumns()
                : Collections.<String>emptyList();
        if (!primaryColumns.isEmpty()) {
            metadataView.setKeyColumns(new ArrayList<String>(primaryColumns));
            metadataView.setReadOnly(Boolean.FALSE);
            return metadataView;
        }

        List<String> uniqueColumns = indexes.values().stream()
                .filter(index -> Boolean.TRUE.equals(index.getUnique()))
                .filter(index -> !Boolean.TRUE.equals(index.getPrimaryKey()))
                .map(MysqlIndexView::getColumns)
                .findFirst()
                .orElse(Collections.<String>emptyList());
        metadataView.setKeyColumns(new ArrayList<String>(uniqueColumns));
        boolean readOnly = uniqueColumns.isEmpty();
        metadataView.setReadOnly(readOnly);
        metadataView.setReadOnlyReason(readOnly ? "该表缺少主键或唯一索引，仅支持只读浏览" : "");
        return metadataView;
    }

    public String getTableDdl(String schema, String table) {
        MysqlIdentifierUtils.validateIdentifier(schema, "schema 名称不合法");
        MysqlIdentifierUtils.validateIdentifier(table, "表名不合法");
        if (!tableExists(schema, table)) {
            throw new CustomException(AppHttpCodeEnum.DATA_NOT_EXIST, "表不存在: " + schema + "." + table);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SHOW CREATE TABLE " + MysqlIdentifierUtils.qualifyTable(schema, table));
        if (rows.isEmpty()) {
            throw new CustomException(AppHttpCodeEnum.DATA_NOT_EXIST, "建表语句不存在");
        }
        Object ddl = rows.get(0).get("Create Table");
        return ddl == null ? "" : String.valueOf(ddl);
    }

    public MysqlTableDataPageView queryTableData(MysqlTableQueryRequest request) {
        validateTableRequest(request.getSchema(), request.getTable());
        MysqlTableMetadataView metadataView = getTableMetadata(request.getSchema(), request.getTable());
        Set<String> allowedColumns = metadataView.getColumns().stream().map(MysqlColumnView::getName).collect(Collectors.toCollection(LinkedHashSet::new));

        int page = request.getPage() == null || request.getPage() < 1 ? 1 : request.getPage();
        int pageSize = request.getPageSize() == null || request.getPageSize() < 1 ? 50 : Math.min(request.getPageSize(), 10000);
        int offset = (page - 1) * pageSize;

        List<Object> parameters = new ArrayList<Object>();
        String whereSql = buildWhereClause(request.getFilters(), allowedColumns, parameters);
        String orderSql = buildOrderClause(request.getSorts(), allowedColumns);

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + MysqlIdentifierUtils.qualifyTable(request.getSchema(), request.getTable()) + whereSql,
                parameters.toArray(),
                Long.class
        );

        List<Object> queryParameters = new ArrayList<Object>(parameters);
        queryParameters.add(offset);
        queryParameters.add(pageSize);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM " + MysqlIdentifierUtils.qualifyTable(request.getSchema(), request.getTable()) + whereSql + orderSql + " LIMIT ?, ?",
                queryParameters.toArray()
        );

        MysqlTableDataPageView pageView = new MysqlTableDataPageView();
        pageView.setSchema(request.getSchema());
        pageView.setTable(request.getTable());
        pageView.setPage(page);
        pageView.setPageSize(pageSize);
        pageView.setTotal(total == null ? 0L : total);
        pageView.setReadOnly(metadataView.getReadOnly());
        pageView.setReadOnlyReason(metadataView.getReadOnlyReason());
        pageView.setKeyColumns(metadataView.getKeyColumns());
        for (Map<String, Object> row : rows) {
            pageView.getRows().add(new LinkedHashMap<String, Object>(row));
        }
        return pageView;
    }

    public MysqlWriteResultView insertRow(MysqlRowInsertRequest request) {
        validateTableRequest(request.getSchema(), request.getTable());
        if (request.getValues() == null || request.getValues().isEmpty()) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "新增数据不能为空");
        }
        MysqlTableMetadataView metadataView = getTableMetadata(request.getSchema(), request.getTable());
        Set<String> allowedColumns = metadataView.getColumns().stream().map(MysqlColumnView::getName).collect(Collectors.toCollection(LinkedHashSet::new));
        MysqlIdentifierUtils.validateIdentifiers(request.getValues().keySet(), "字段名不合法");
        request.getValues().keySet().forEach(column -> ensureAllowedColumn(column, allowedColumns));

        List<String> columns = new ArrayList<String>(request.getValues().keySet());
        List<String> placeholders = columns.stream().map(column -> "?").collect(Collectors.toList());
        List<Object> values = columns.stream().map(column -> request.getValues().get(column)).collect(Collectors.toList());
        int affectedRows = jdbcTemplate.update(
                "INSERT INTO " + MysqlIdentifierUtils.qualifyTable(request.getSchema(), request.getTable())
                        + " (" + columns.stream().map(MysqlIdentifierUtils::quoteIdentifier).collect(Collectors.joining(", ")) + ") "
                        + "VALUES (" + String.join(", ", placeholders) + ")",
                values.toArray()
        );
        MysqlWriteResultView resultView = new MysqlWriteResultView();
        resultView.setAffectedRows((long) affectedRows);
        return resultView;
    }

    public MysqlWriteResultView updateRow(MysqlRowUpdateRequest request) {
        validateTableRequest(request.getSchema(), request.getTable());
        if (request.getValues() == null || request.getValues().isEmpty()) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "修改内容不能为空");
        }
        MysqlTableMetadataView metadataView = getTableMetadata(request.getSchema(), request.getTable());
        ensureWritable(metadataView);
        validateKeyValues(request.getKeyValues(), metadataView.getKeyColumns());
        Set<String> allowedColumns = metadataView.getColumns().stream().map(MysqlColumnView::getName).collect(Collectors.toCollection(LinkedHashSet::new));
        request.getValues().keySet().forEach(column -> {
            MysqlIdentifierUtils.validateIdentifier(column, "字段名不合法");
            ensureAllowedColumn(column, allowedColumns);
        });

        List<Object> parameters = new ArrayList<Object>();
        String setClause = request.getValues().entrySet().stream()
                .map(entry -> {
                    parameters.add(entry.getValue());
                    return MysqlIdentifierUtils.quoteIdentifier(entry.getKey()) + " = ?";
                })
                .collect(Collectors.joining(", "));
        String whereClause = buildKeyWhereClause(request.getKeyValues(), parameters);
        int affectedRows = jdbcTemplate.update(
                "UPDATE " + MysqlIdentifierUtils.qualifyTable(request.getSchema(), request.getTable()) + " SET " + setClause + whereClause,
                parameters.toArray()
        );
        MysqlWriteResultView resultView = new MysqlWriteResultView();
        resultView.setAffectedRows((long) affectedRows);
        return resultView;
    }

    public MysqlWriteResultView deleteRow(MysqlRowDeleteRequest request) {
        validateTableRequest(request.getSchema(), request.getTable());
        MysqlTableMetadataView metadataView = getTableMetadata(request.getSchema(), request.getTable());
        ensureWritable(metadataView);
        validateKeyValues(request.getKeyValues(), metadataView.getKeyColumns());
        List<Object> parameters = new ArrayList<Object>();
        String whereClause = buildKeyWhereClause(request.getKeyValues(), parameters);
        int affectedRows = jdbcTemplate.update(
                "DELETE FROM " + MysqlIdentifierUtils.qualifyTable(request.getSchema(), request.getTable()) + whereClause,
                parameters.toArray()
        );
        MysqlWriteResultView resultView = new MysqlWriteResultView();
        resultView.setAffectedRows((long) affectedRows);
        return resultView;
    }

    public MysqlDesignPreviewView previewDesign(MysqlTableDesignRequest request) {
        validateTableRequest(request.getSchema(), request.getTable());
        boolean tableExists = tableExists(request.getSchema(), request.getTable());
        MysqlTableMetadataView currentMetadata = tableExists ? getTableMetadata(request.getSchema(), request.getTable()) : new MysqlTableMetadataView();
        return MysqlTableDesignSqlBuilder.buildPreview(request, currentMetadata, tableExists);
    }

    public MysqlSqlBatchResultView executeDesign(MysqlTableDesignRequest request, String executedBy) {
        MysqlDesignPreviewView previewView = previewDesign(request);
        if (previewView.getStatements().isEmpty()) {
            MysqlSqlBatchResultView resultView = new MysqlSqlBatchResultView();
            resultView.setSchema(request.getSchema());
            resultView.setDangerous(Boolean.TRUE);
            resultView.setSuccess(Boolean.TRUE);
            resultView.setStatus("SUCCESS");
            resultView.setMessage("当前没有结构变更");
            resultView.setStatementCount(0);
            return resultView;
        }
        if (!Boolean.TRUE.equals(request.getConfirmed())) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "设计器生成的 SQL 需要确认后才能执行");
        }
        MysqlSqlExecuteRequest executeRequest = new MysqlSqlExecuteRequest();
        executeRequest.setSchema(request.getSchema());
        executeRequest.setSql(String.join(";\n", previewView.getStatements()));
        executeRequest.setConfirmed(Boolean.TRUE);
        return executeSql(executeRequest, executedBy);
    }

    public MysqlSqlBatchResultView executeSql(MysqlSqlExecuteRequest request, String executedBy) {
        String schema = request.getSchema();
        if (schema != null && !schema.trim().isEmpty()) {
            MysqlIdentifierUtils.validateIdentifier(schema, "schema 名称不合法");
            if (!schemaExists(schema)) {
                throw new CustomException(AppHttpCodeEnum.DATA_NOT_EXIST, "schema 不存在: " + schema);
            }
        }
        List<String> statements = SqlStatementSplitter.split(request.getSql());
        if (statements.isEmpty()) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "SQL 不能为空");
        }

        boolean dangerous = statements.stream()
                .map(MysqlSqlDangerInspector::inspect)
                .anyMatch(MysqlSqlDangerInspector.InspectionResult::isDangerous);
        if (dangerous && !Boolean.TRUE.equals(request.getConfirmed())) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "SQL 包含高风险语句，请确认后再执行");
        }

        LocalDateTime startedAt = LocalDateTime.now();
        long batchId = mysqlHistoryService.startBatch(schema, request.getSql(), statements.size(), dangerous, executedBy, startedAt);
        MysqlSqlBatchResultView batchResultView = new MysqlSqlBatchResultView();
        batchResultView.setBatchId(batchId);
        batchResultView.setSchema(schema);
        batchResultView.setDangerous(dangerous);
        batchResultView.setStatementCount(statements.size());
        batchResultView.setSuccess(Boolean.TRUE);
        batchResultView.setStatus("RUNNING");

        String status = "SUCCESS";
        String originalCatalog = null;
        try (Connection connection = getConnection()) {
            if (schema != null && !schema.trim().isEmpty()) {
                originalCatalog = connection.getCatalog();
                connection.setCatalog(schema);
            }

            int maxRows = request.getMaxDisplayRows() != null && request.getMaxDisplayRows() > 0
                    ? request.getMaxDisplayRows() : 1000;

            for (int index = 0; index < statements.size(); index++) {
                String statementSql = statements.get(index);
                MysqlSqlDangerInspector.InspectionResult inspectionResult = MysqlSqlDangerInspector.inspect(statementSql);
                MysqlSqlStatementResultView statementResultView = executeSingleStatement(connection, index + 1, statementSql, inspectionResult, maxRows);
                batchResultView.getResults().add(statementResultView);
                mysqlHistoryService.recordStatement(
                        batchId,
                        index + 1,
                        statementSql,
                        inspectionResult.getStatementType(),
                        Boolean.TRUE.equals(statementResultView.getSuccess()),
                        statementResultView.getAffectedRows() == null ? 0L : statementResultView.getAffectedRows(),
                        statementResultView.getRows().size(),
                        statementResultView.getDurationMs() == null ? 0L : statementResultView.getDurationMs(),
                        Boolean.TRUE.equals(statementResultView.getSuccess()) ? null : statementResultView.getMessage(),
                        statementResultView.getError() == null ? null : statementResultView.getError().getErrorCode(),
                        statementResultView.getError() == null ? null : statementResultView.getError().getSqlState(),
                        statementResultView.getError() == null ? null : statementResultView.getError().getCategory()
                );
                if (!Boolean.TRUE.equals(statementResultView.getSuccess())) {
                    status = "FAILED";
                    batchResultView.setSuccess(Boolean.FALSE);
                    batchResultView.setStatus(status);
                    batchResultView.setFailedStatementIndex(index + 1);
                    batchResultView.setMessage(statementResultView.getMessage());
                    break;
                }
            }
        } catch (SQLException e) {
            status = "FAILED";
            batchResultView.setSuccess(Boolean.FALSE);
            batchResultView.setStatus(status);
            batchResultView.setMessage(MysqlSqlErrorFormatter.toDisplayMessage(e));
            throw new CustomException(AppHttpCodeEnum.SERVER_ERROR, e.getMessage());
        } finally {
            if (originalCatalog != null) {
                try (Connection conn = getConnection()) {
                    conn.setCatalog(originalCatalog);
                } catch (SQLException ignored) {
                }
            }
            mysqlHistoryService.finishBatch(batchId, status, LocalDateTime.now());
        }
        if (!"FAILED".equals(status)) {
            batchResultView.setStatus(status);
            batchResultView.setMessage("执行成功，共 " + statements.size() + " 条语句");
        }
        return batchResultView;
    }

    public MysqlHistoryPageView listHistory(MysqlHistoryQueryRequest request) {
        return mysqlHistoryService.listHistory(request);
    }

    public MysqlHistoryDetailView getHistoryDetail(long batchId) {
        return mysqlHistoryService.getHistoryDetail(batchId);
    }

    private MysqlSqlStatementResultView executeSingleStatement(Connection connection,
                                                               int statementIndex,
                                                               String sql,
                                                               MysqlSqlDangerInspector.InspectionResult inspectionResult,
                                                               int maxRows) {
        long startedAt = System.currentTimeMillis();
        MysqlSqlStatementResultView resultView = new MysqlSqlStatementResultView();
        resultView.setIndex(statementIndex);
        resultView.setSql(sql);
        resultView.setType(inspectionResult.getStatementType());
        resultView.setDangerous(inspectionResult.isDangerous());

        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(60);
            boolean hasResultSet = statement.execute(sql);
            if (hasResultSet) {
                try (ResultSet resultSet = statement.getResultSet()) {
                    ResultSetMetaData metaData = resultSet.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
                        resultView.getColumns().add(metaData.getColumnLabel(columnIndex));
                    }
                    long totalRowCount = 0;
                    while (resultSet.next()) {
                        totalRowCount++;
                        if (resultView.getRows().size() < maxRows) {
                            LinkedHashMap<String, Object> row = new LinkedHashMap<String, Object>();
                            for (int columnIndex = 1; columnIndex <= columnCount; columnIndex++) {
                                row.put(metaData.getColumnLabel(columnIndex), resultSet.getObject(columnIndex));
                            }
                            resultView.getRows().add(row);
                        }
                    }
                    resultView.setTotalRowCount(totalRowCount);
                    if (totalRowCount > maxRows) {
                        resultView.setMessage("返回 " + resultView.getRows().size() + " 行结果，共 " + totalRowCount + " 行");
                    } else {
                        resultView.setMessage("返回 " + resultView.getRows().size() + " 行结果");
                    }
                    resultView.setAffectedRows(0L);
                }
            } else {
                long affectedRows = statement.getUpdateCount();
                resultView.setAffectedRows(affectedRows);
                resultView.setMessage("影响 " + affectedRows + " 行");
            }
            resultView.setSuccess(Boolean.TRUE);
        } catch (SQLException e) {
            MysqlSqlErrorView errorView = MysqlSqlErrorFormatter.format(e);
            resultView.setSuccess(Boolean.FALSE);
            resultView.setError(errorView);
            resultView.setMessage(MysqlSqlErrorFormatter.toDisplayMessage(e));
            resultView.setAffectedRows(0L);
        }
        resultView.setDurationMs(System.currentTimeMillis() - startedAt);
        return resultView;
    }

    private String buildWhereClause(List<MysqlTableFilterRequest> filters, Set<String> allowedColumns, List<Object> parameters) {
        if (filters == null || filters.isEmpty()) {
            return "";
        }
        List<String> clauses = new ArrayList<String>();
        for (MysqlTableFilterRequest filter : filters) {
            if (filter == null || filter.getColumn() == null || filter.getOperator() == null) {
                continue;
            }
            MysqlIdentifierUtils.validateIdentifier(filter.getColumn(), "筛选字段不合法");
            ensureAllowedColumn(filter.getColumn(), allowedColumns);
            String operator = filter.getOperator().trim().toLowerCase(Locale.ROOT);
            String columnSql = MysqlIdentifierUtils.quoteIdentifier(filter.getColumn());
            if ("eq".equals(operator)) {
                clauses.add(columnSql + " = ?");
                parameters.add(filter.getValue());
            } else if ("ne".equals(operator)) {
                clauses.add(columnSql + " <> ?");
                parameters.add(filter.getValue());
            } else if ("like".equals(operator)) {
                clauses.add(columnSql + " LIKE ?");
                parameters.add("%" + String.valueOf(filter.getValue()) + "%");
            } else if ("gt".equals(operator) || "gte".equals(operator) || "lt".equals(operator) || "lte".equals(operator)) {
                clauses.add(columnSql + " " + toSqlOperator(operator) + " ?");
                parameters.add(filter.getValue());
            } else if ("isnull".equals(operator)) {
                clauses.add(columnSql + " IS NULL");
            } else if ("notnull".equals(operator)) {
                clauses.add(columnSql + " IS NOT NULL");
            } else {
                throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "不支持的筛选操作");
            }
        }
        if (clauses.isEmpty()) {
            return "";
        }
        return " WHERE " + String.join(" AND ", clauses);
    }

    private String buildOrderClause(List<MysqlTableSortRequest> sorts, Set<String> allowedColumns) {
        if (sorts == null || sorts.isEmpty()) {
            return "";
        }
        List<String> clauses = new ArrayList<String>();
        for (MysqlTableSortRequest sort : sorts) {
            if (sort == null || sort.getColumn() == null) {
                continue;
            }
            MysqlIdentifierUtils.validateIdentifier(sort.getColumn(), "排序字段不合法");
            ensureAllowedColumn(sort.getColumn(), allowedColumns);
            String direction = "DESC".equalsIgnoreCase(sort.getDirection()) ? "DESC" : "ASC";
            clauses.add(MysqlIdentifierUtils.quoteIdentifier(sort.getColumn()) + " " + direction);
        }
        if (clauses.isEmpty()) {
            return "";
        }
        return " ORDER BY " + String.join(", ", clauses);
    }

    private String buildKeyWhereClause(LinkedHashMap<String, Object> keyValues, List<Object> parameters) {
        List<String> clauses = new ArrayList<String>();
        for (Map.Entry<String, Object> entry : keyValues.entrySet()) {
            MysqlIdentifierUtils.validateIdentifier(entry.getKey(), "定位字段不合法");
            if (entry.getValue() == null) {
                clauses.add(MysqlIdentifierUtils.quoteIdentifier(entry.getKey()) + " IS NULL");
            } else {
                clauses.add(MysqlIdentifierUtils.quoteIdentifier(entry.getKey()) + " = ?");
                parameters.add(entry.getValue());
            }
        }
        return " WHERE " + String.join(" AND ", clauses);
    }

    private void validateTableRequest(String schema, String table) {
        MysqlIdentifierUtils.validateIdentifier(schema, "schema 名称不合法");
        MysqlIdentifierUtils.validateIdentifier(table, "表名不合法");
    }

    private void ensureWritable(MysqlTableMetadataView metadataView) {
        if (Boolean.TRUE.equals(metadataView.getReadOnly())) {
            throw new CustomException(AppHttpCodeEnum.NO_OPERATOR_AUTH, metadataView.getReadOnlyReason());
        }
    }

    private void validateKeyValues(LinkedHashMap<String, Object> keyValues, List<String> requiredKeys) {
        if (keyValues == null || keyValues.isEmpty()) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "缺少行定位字段");
        }
        for (String requiredKey : requiredKeys) {
            if (!keyValues.containsKey(requiredKey)) {
                throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "定位字段不完整");
            }
        }
    }

    private void ensureAllowedColumn(String column, Set<String> allowedColumns) {
        if (!allowedColumns.contains(column)) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "字段不存在: " + column);
        }
    }

    private Integer asInteger(Object... values) {
        for (Object value : values) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
        }
        return null;
    }

    private String toSqlOperator(String operator) {
        if ("gt".equals(operator)) {
            return ">";
        }
        if ("gte".equals(operator)) {
            return ">=";
        }
        if ("lt".equals(operator)) {
            return "<";
        }
        if ("lte".equals(operator)) {
            return "<=";
        }
        return "=";
    }

    private boolean tableExists(String schema, String table) {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ? AND table_type = 'BASE TABLE'",
                Long.class,
                schema,
                table
        );
        return total != null && total > 0;
    }

    private boolean schemaExists(String schema) {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?",
                Long.class,
                schema
        );
        return total != null && total > 0;
    }

    private Connection getConnection() throws SQLException {
        DataSource dataSource = jdbcTemplate.getDataSource();
        if (dataSource == null) {
            throw new SQLException("数据源未初始化");
        }
        return dataSource.getConnection();
    }
}
