package com.tool.otsutil.mysqlworkbench.service;

import com.tool.otsutil.mysqlworkbench.model.request.MysqlHistoryQueryRequest;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlHistoryBatchView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlHistoryDetailView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlHistoryPageView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlHistoryStatementView;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MysqlHistoryService {

    public static final String BATCH_TABLE_NAME = "mysql_query_history";

    public static final String STATEMENT_TABLE_NAME = "mysql_query_history_statement";

    private final JdbcTemplate jdbcTemplate;

    public MysqlHistoryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long startBatch(String schemaName,
                           String batchText,
                           int statementCount,
                           boolean dangerous,
                           String executedBy,
                           LocalDateTime startedAt) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO " + BATCH_TABLE_NAME
                            + " (schema_name, batch_text, statement_count, dangerous, executed_by, status, started_at, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, schemaName);
            statement.setString(2, batchText);
            statement.setInt(3, statementCount);
            statement.setBoolean(4, dangerous);
            statement.setString(5, executedBy);
            statement.setString(6, "RUNNING");
            statement.setTimestamp(7, Timestamp.valueOf(startedAt));
            statement.setTimestamp(8, Timestamp.valueOf(startedAt));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? 0L : key.longValue();
    }

    public void recordStatement(long batchId,
                                int order,
                                String sql,
                                String statementType,
                                boolean success,
                                long affectedRows,
                                int resultSize,
                                long durationMs,
                                String errorMessage,
                                Integer errorCode,
                                String sqlState,
                                String errorCategory) {
        jdbcTemplate.update(
                "INSERT INTO " + STATEMENT_TABLE_NAME
                        + " (history_id, statement_order, statement_text, statement_type, success, affected_rows, result_size, duration_ms, error_message, error_code, sql_state, error_category, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                batchId,
                order,
                sql,
                statementType,
                success,
                affectedRows,
                resultSize,
                durationMs,
                errorMessage,
                errorCode,
                sqlState,
                errorCategory,
                Timestamp.valueOf(LocalDateTime.now())
        );
    }

    public void finishBatch(long batchId, String status, LocalDateTime finishedAt) {
        jdbcTemplate.update(
                "UPDATE " + BATCH_TABLE_NAME + " SET status = ?, finished_at = ? WHERE id = ?",
                status,
                Timestamp.valueOf(finishedAt),
                batchId
        );
    }

    public MysqlHistoryPageView listHistory(MysqlHistoryQueryRequest request) {
        int page = request == null || request.getPage() == null || request.getPage() < 1 ? 1 : request.getPage();
        int pageSize = request == null || request.getPageSize() == null || request.getPageSize() < 1 ? 20 : Math.min(request.getPageSize(), 100);
        int offset = (page - 1) * pageSize;

        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + BATCH_TABLE_NAME, Long.class);
        List<MysqlHistoryBatchView> items = jdbcTemplate.query(
                "SELECT id, schema_name, executed_by, status, dangerous, statement_count, batch_text, started_at, finished_at "
                        + "FROM " + BATCH_TABLE_NAME + " ORDER BY id DESC LIMIT ? OFFSET ?",
                (resultSet, rowNum) -> {
                    MysqlHistoryBatchView view = new MysqlHistoryBatchView();
                    view.setId(resultSet.getLong("id"));
                    view.setSchemaName(resultSet.getString("schema_name"));
                    view.setExecutedBy(resultSet.getString("executed_by"));
                    view.setStatus(resultSet.getString("status"));
                    view.setDangerous(resultSet.getBoolean("dangerous"));
                    view.setStatementCount(resultSet.getInt("statement_count"));
                    String batchText = resultSet.getString("batch_text");
                    if (batchText != null) {
                        view.setStatementPreview(batchText.length() > 120 ? batchText.substring(0, 120) + "..." : batchText);
                    }
                    Timestamp startedAt = resultSet.getTimestamp("started_at");
                    Timestamp finishedAt = resultSet.getTimestamp("finished_at");
                    view.setStartedAt(startedAt == null ? null : startedAt.toLocalDateTime());
                    view.setFinishedAt(finishedAt == null ? null : finishedAt.toLocalDateTime());
                    return view;
                },
                pageSize,
                offset
        );

        MysqlHistoryPageView pageView = new MysqlHistoryPageView();
        pageView.setPage(page);
        pageView.setPageSize(pageSize);
        pageView.setTotal(total == null ? 0L : total);
        pageView.setItems(items);
        return pageView;
    }

    public MysqlHistoryDetailView getHistoryDetail(long batchId) {
        MysqlHistoryDetailView detailView = jdbcTemplate.queryForObject(
                "SELECT id, schema_name, executed_by, status, dangerous, batch_text, started_at, finished_at "
                        + "FROM " + BATCH_TABLE_NAME + " WHERE id = ?",
                (resultSet, rowNum) -> {
                    MysqlHistoryDetailView view = new MysqlHistoryDetailView();
                    view.setId(resultSet.getLong("id"));
                    view.setSchemaName(resultSet.getString("schema_name"));
                    view.setExecutedBy(resultSet.getString("executed_by"));
                    view.setStatus(resultSet.getString("status"));
                    view.setDangerous(resultSet.getBoolean("dangerous"));
                    view.setBatchText(resultSet.getString("batch_text"));
                    Timestamp startedAt = resultSet.getTimestamp("started_at");
                    Timestamp finishedAt = resultSet.getTimestamp("finished_at");
                    view.setStartedAt(startedAt == null ? null : startedAt.toLocalDateTime());
                    view.setFinishedAt(finishedAt == null ? null : finishedAt.toLocalDateTime());
                    return view;
                },
                batchId
        );

        List<MysqlHistoryStatementView> statements = jdbcTemplate.query(
                "SELECT id, statement_order, statement_type, success, affected_rows, result_size, duration_ms, error_message, error_code, sql_state, error_category, statement_text "
                        + "FROM " + STATEMENT_TABLE_NAME + " WHERE history_id = ? ORDER BY statement_order ASC",
                (resultSet, rowNum) -> {
                    MysqlHistoryStatementView view = new MysqlHistoryStatementView();
                    view.setId(resultSet.getLong("id"));
                    view.setStatementOrder(resultSet.getInt("statement_order"));
                    view.setStatementType(resultSet.getString("statement_type"));
                    view.setSuccess(resultSet.getBoolean("success"));
                    view.setAffectedRows(resultSet.getLong("affected_rows"));
                    view.setResultSize(resultSet.getInt("result_size"));
                    view.setDurationMs(resultSet.getLong("duration_ms"));
                    view.setErrorMessage(resultSet.getString("error_message"));
                    int errorCode = resultSet.getInt("error_code");
                    view.setErrorCode(resultSet.wasNull() ? null : errorCode);
                    view.setSqlState(resultSet.getString("sql_state"));
                    view.setErrorCategory(resultSet.getString("error_category"));
                    view.setStatementText(resultSet.getString("statement_text"));
                    return view;
                },
                batchId
        );
        detailView.setStatements(statements);
        return detailView;
    }
}
