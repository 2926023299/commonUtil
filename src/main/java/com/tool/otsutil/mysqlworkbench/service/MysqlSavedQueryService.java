package com.tool.otsutil.mysqlworkbench.service;

import com.tool.otsutil.exception.CustomException;
import com.tool.otsutil.model.common.AppHttpCodeEnum;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlSavedQuerySaveRequest;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlSavedQueryView;
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
public class MysqlSavedQueryService {

    public static final String TABLE_NAME = "mysql_saved_query";

    private final JdbcTemplate jdbcTemplate;

    public MysqlSavedQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MysqlSavedQueryView> listQueries() {
        return jdbcTemplate.query(
                "SELECT id, title, schema_name, sql_text, created_at, updated_at "
                        + "FROM " + TABLE_NAME + " ORDER BY updated_at DESC, id DESC",
                (resultSet, rowNum) -> {
                    MysqlSavedQueryView view = new MysqlSavedQueryView();
                    view.setId(resultSet.getLong("id"));
                    view.setTitle(resultSet.getString("title"));
                    view.setSchemaName(resultSet.getString("schema_name"));
                    view.setSqlText(resultSet.getString("sql_text"));
                    view.setStatementPreview(buildPreview(view.getSqlText()));
                    Timestamp createdAt = resultSet.getTimestamp("created_at");
                    Timestamp updatedAt = resultSet.getTimestamp("updated_at");
                    view.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
                    view.setUpdatedAt(updatedAt == null ? null : updatedAt.toLocalDateTime());
                    return view;
                }
        );
    }

    public MysqlSavedQueryView saveQuery(MysqlSavedQuerySaveRequest request) {
        if (request == null) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "查询保存参数不能为空");
        }
        String sqlText = request.getSqlText() == null ? "" : request.getSqlText().trim();
        if (sqlText.isEmpty()) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "查询 SQL 不能为空");
        }
        String title = normalizeTitle(request.getTitle());
        String schemaName = normalizeBlank(request.getSchemaName());
        LocalDateTime now = LocalDateTime.now();

        if (request.getId() == null) {
            long id = insertQuery(title, schemaName, sqlText, now);
            return getQuery(id);
        }

        int updated = jdbcTemplate.update(
                "UPDATE " + TABLE_NAME + " SET title = ?, schema_name = ?, sql_text = ?, updated_at = ? WHERE id = ?",
                title,
                schemaName,
                sqlText,
                Timestamp.valueOf(now),
                request.getId()
        );
        if (updated == 0) {
            throw new CustomException(AppHttpCodeEnum.DATA_NOT_EXIST, "保存的查询不存在: " + request.getId());
        }
        return getQuery(request.getId());
    }

    public MysqlSavedQueryView getQuery(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT id, title, schema_name, sql_text, created_at, updated_at FROM " + TABLE_NAME + " WHERE id = ?",
                (resultSet, rowNum) -> {
                    MysqlSavedQueryView view = new MysqlSavedQueryView();
                    view.setId(resultSet.getLong("id"));
                    view.setTitle(resultSet.getString("title"));
                    view.setSchemaName(resultSet.getString("schema_name"));
                    view.setSqlText(resultSet.getString("sql_text"));
                    view.setStatementPreview(buildPreview(view.getSqlText()));
                    Timestamp createdAt = resultSet.getTimestamp("created_at");
                    Timestamp updatedAt = resultSet.getTimestamp("updated_at");
                    view.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
                    view.setUpdatedAt(updatedAt == null ? null : updatedAt.toLocalDateTime());
                    return view;
                },
                id
        );
    }

    public void deleteQuery(long id) {
        jdbcTemplate.update("DELETE FROM " + TABLE_NAME + " WHERE id = ?", id);
    }

    private long insertQuery(String title, String schemaName, String sqlText, LocalDateTime now) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO " + TABLE_NAME + " (title, schema_name, sql_text, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, title);
            statement.setString(2, schemaName);
            statement.setString(3, sqlText);
            statement.setTimestamp(4, Timestamp.valueOf(now));
            statement.setTimestamp(5, Timestamp.valueOf(now));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new CustomException(AppHttpCodeEnum.SERVER_ERROR, "保存查询后未返回主键");
        }
        return key.longValue();
    }

    private String normalizeTitle(String title) {
        String normalized = title == null ? "" : title.trim();
        if (normalized.isEmpty()) {
            return "query.sql";
        }
        return normalized.length() > 120 ? normalized.substring(0, 120) : normalized;
    }

    private String normalizeBlank(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String buildPreview(String sqlText) {
        if (sqlText == null) {
            return "";
        }
        String preview = sqlText.replaceAll("\\s+", " ").trim();
        return preview.length() > 160 ? preview.substring(0, 160) + "..." : preview;
    }
}
