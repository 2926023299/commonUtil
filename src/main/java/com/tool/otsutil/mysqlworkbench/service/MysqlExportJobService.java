package com.tool.otsutil.mysqlworkbench.service;

import com.alibaba.fastjson2.JSON;
import com.tool.otsutil.exception.CustomException;
import com.tool.otsutil.model.common.AppHttpCodeEnum;
import com.tool.otsutil.mysqlworkbench.config.MysqlWorkbenchProperties;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlExportCreateRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlTableFilterRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlTableSortRequest;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlExportJobView;
import com.tool.otsutil.mysqlworkbench.util.MysqlIdentifierUtils;
import com.tool.otsutil.mysqlworkbench.util.MysqlSqlDangerInspector;
import com.tool.otsutil.mysqlworkbench.util.SqlStatementSplitter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MysqlExportJobService {

    private static final String TABLE_NAME = "mysql_export_job";

    private static final int PROGRESS_UPDATE_INTERVAL = 1000;

    private static final String MYSQL_IDENTIFIER_PATTERN = "(?:`[^`]+`|[A-Za-z0-9_$]+)";

    private static final Pattern CREATE_TABLE_NAME_PATTERN = Pattern.compile(
            "(?is)^(\\s*CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?)("
                    + MYSQL_IDENTIFIER_PATTERN
                    + "(?:\\s*\\.\\s*"
                    + MYSQL_IDENTIFIER_PATTERN
                    + ")?)"
    );

    private final JdbcTemplate jdbcTemplate;

    private final MysqlWorkbenchService mysqlWorkbenchService;

    private final MysqlWorkbenchProperties properties;

    private final ExecutorService executorService;

    public MysqlExportJobService(JdbcTemplate jdbcTemplate,
                                 MysqlWorkbenchService mysqlWorkbenchService,
                                 MysqlWorkbenchProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.mysqlWorkbenchService = mysqlWorkbenchService;
        this.properties = properties;
        int poolSize = Math.max(1, properties.getExport().getThreadPoolSize());
        this.executorService = Executors.newFixedThreadPool(poolSize);
    }

    public MysqlExportJobView createJob(MysqlExportCreateRequest request, String createdBy) {
        ExportRequest normalized = normalizeRequest(request);
        LocalDateTime now = LocalDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO " + TABLE_NAME
                            + " (source_type, format, schema_name, table_name, sql_text, filters_json, sorts_json, status, exported_rows, created_by, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, normalized.sourceType);
            statement.setString(2, normalized.format);
            statement.setString(3, normalized.schema);
            statement.setString(4, normalized.table);
            statement.setString(5, normalized.sql);
            statement.setString(6, JSON.toJSONString(normalized.filters));
            statement.setString(7, JSON.toJSONString(normalized.sorts));
            statement.setString(8, "PENDING");
            statement.setLong(9, 0L);
            statement.setString(10, createdBy);
            statement.setTimestamp(11, Timestamp.valueOf(now));
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new CustomException(AppHttpCodeEnum.SERVER_ERROR, "创建导出任务后未返回主键");
        }
        long jobId = key.longValue();
        executorService.submit(() -> runJob(jobId));
        return getJob(jobId);
    }

    public List<MysqlExportJobView> listJobs(Integer page, Integer pageSize) {
        int normalizedPage = page == null || page < 1 ? 1 : page;
        int normalizedSize = pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
        int offset = (normalizedPage - 1) * normalizedSize;
        return jdbcTemplate.query(
                "SELECT id, source_type, format, schema_name, table_name, sql_text, status, file_name, file_size, total_rows, exported_rows, message, created_by, created_at, started_at, finished_at "
                        + "FROM " + TABLE_NAME + " ORDER BY id DESC LIMIT ? OFFSET ?",
                (resultSet, rowNum) -> toView(resultSet),
                normalizedSize,
                offset
        );
    }

    public MysqlExportJobView getJob(long jobId) {
        return jdbcTemplate.queryForObject(
                "SELECT id, source_type, format, schema_name, table_name, sql_text, status, file_name, file_size, total_rows, exported_rows, message, created_by, created_at, started_at, finished_at "
                        + "FROM " + TABLE_NAME + " WHERE id = ?",
                (resultSet, rowNum) -> toView(resultSet),
                jobId
        );
    }

    public Path getDownloadFile(long jobId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, file_path FROM " + TABLE_NAME + " WHERE id = ?",
                jobId
        );
        if (!"SUCCESS".equals(String.valueOf(row.get("status")))) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "导出任务尚未完成");
        }
        Object filePath = row.get("file_path");
        if (filePath == null) {
            throw new CustomException(AppHttpCodeEnum.DATA_NOT_EXIST, "导出文件不存在");
        }
        Path path = Paths.get(String.valueOf(filePath));
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new CustomException(AppHttpCodeEnum.DATA_NOT_EXIST, "导出文件不存在");
        }
        return path;
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    private void runJob(long jobId) {
        try {
            markRunning(jobId);
            ExportJobRecord record = loadRecord(jobId);
            Path exportDirectory = resolveExportDirectory();
            Files.createDirectories(exportDirectory);
            String fileName = buildFileName(record);
            Path filePath = exportDirectory.resolve(fileName);
            long rows = writeExport(record, filePath);
            long fileSize = Files.size(filePath);
            jdbcTemplate.update(
                    "UPDATE " + TABLE_NAME + " SET status = ?, file_name = ?, file_path = ?, file_size = ?, total_rows = ?, exported_rows = ?, message = ?, finished_at = ? WHERE id = ?",
                    "SUCCESS",
                    fileName,
                    filePath.toString(),
                    fileSize,
                    rows,
                    rows,
                    "导出完成",
                    Timestamp.valueOf(LocalDateTime.now()),
                    jobId
            );
        } catch (Exception e) {
            jdbcTemplate.update(
                    "UPDATE " + TABLE_NAME + " SET status = ?, message = ?, finished_at = ? WHERE id = ?",
                    "FAILED",
                    e.getMessage() == null ? "导出失败" : truncate(e.getMessage(), 1000),
                    Timestamp.valueOf(LocalDateTime.now()),
                    jobId
            );
        }
    }

    private long writeExport(ExportJobRecord record, Path filePath) throws Exception {
        List<Object> parameters = new ArrayList<Object>();
        String sql = buildExportSql(record, parameters);
        DataSource dataSource = jdbcTemplate.getDataSource();
        if (dataSource == null) {
            throw new SQLException("数据源未初始化");
        }
        try (Connection connection = dataSource.getConnection()) {
            String originalCatalog = null;
            if (record.schema != null && !record.schema.trim().isEmpty()) {
                originalCatalog = connection.getCatalog();
                connection.setCatalog(record.schema);
            }
            try (PreparedStatement statement = connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                applyStreamingFetchSize(statement);
                bindParameters(statement, parameters);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if ("XLSX".equals(record.format)) {
                        return writeXlsx(jobId(record), resultSet, filePath);
                    }
                    if ("SQL".equals(record.format)) {
                        return writeSql(jobId(record), record, resultSet, filePath);
                    }
                    return writeCsv(jobId(record), resultSet, filePath);
                }
            } finally {
                if (originalCatalog != null) {
                    connection.setCatalog(originalCatalog);
                }
            }
        }
    }

    private String buildExportSql(ExportJobRecord record, List<Object> parameters) {
        if ("TABLE".equals(record.sourceType)) {
            return mysqlWorkbenchService.buildTableSelectSql(record.schema, record.table, record.filters, record.sorts, parameters);
        }
        List<String> statements = SqlStatementSplitter.split(record.sql);
        if (statements.size() != 1) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "查询结果导出仅支持单条 SELECT/SHOW 语句");
        }
        String statement = statements.get(0);
        String statementType = MysqlSqlDangerInspector.inspect(statement).getStatementType();
        if (!"SELECT".equals(statementType) && !"SHOW".equals(statementType) && !"WITH".equals(statementType)) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "查询结果导出仅支持 SELECT/SHOW 语句");
        }
        return statement;
    }

    private long writeCsv(long jobId, ResultSet resultSet, Path filePath) throws SQLException, IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                if (i > 1) {
                    writer.write(',');
                }
                writer.write(csvCell(metaData.getColumnLabel(i)));
            }
            writer.newLine();
            long rows = 0;
            while (resultSet.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    if (i > 1) {
                        writer.write(',');
                    }
                    writer.write(csvCell(resultSet.getObject(i)));
                }
                writer.newLine();
                rows++;
                updateProgress(jobId, rows);
            }
            return rows;
        }
    }

    private long writeSql(long jobId, ExportJobRecord record, ResultSet resultSet, Path filePath) throws SQLException, IOException {
        if (!"TABLE".equals(record.sourceType)) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "SQL 格式导出仅支持表数据");
        }
        try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            String tableName = MysqlIdentifierUtils.quoteIdentifier(record.table);
            writer.write("-- MySQL Workbench export");
            writer.newLine();
            writer.write("DROP TABLE IF EXISTS " + tableName + ";");
            writer.newLine();
            writer.write(normalizeCreateTableDdl(mysqlWorkbenchService.getTableDdl(record.schema, record.table), record.table));
            writer.newLine();
            writer.newLine();

            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            StringBuilder columnSql = new StringBuilder();
            for (int i = 1; i <= columnCount; i++) {
                if (i > 1) {
                    columnSql.append(", ");
                }
                columnSql.append(MysqlIdentifierUtils.quoteIdentifier(metaData.getColumnLabel(i)));
            }
            long rows = 0;
            while (resultSet.next()) {
                if (rows == 0) {
                    writer.write("INSERT INTO " + tableName + " (" + columnSql + ") VALUES");
                    writer.newLine();
                } else {
                    writer.write(",");
                    writer.newLine();
                }
                writer.write("(");
                for (int i = 1; i <= columnCount; i++) {
                    if (i > 1) {
                        writer.write(", ");
                    }
                    writer.write(sqlLiteral(resultSet.getObject(i)));
                }
                writer.write(")");
                rows++;
                updateProgress(jobId, rows);
            }
            if (rows > 0) {
                writer.write(";");
                writer.newLine();
            }
            return rows;
        }
    }

    private String normalizeCreateTableDdl(String ddl, String table) {
        String normalizedDdl = ddl == null ? "" : ddl.trim().replaceAll(";?\\s*$", "");
        Matcher matcher = CREATE_TABLE_NAME_PATTERN.matcher(normalizedDdl);
        if (!matcher.find()) {
            throw new CustomException(AppHttpCodeEnum.SERVER_ERROR, "建表语句格式不支持");
        }
        return matcher.group(1)
                + MysqlIdentifierUtils.quoteIdentifier(table)
                + normalizedDdl.substring(matcher.end(2))
                + ";";
    }

    private long writeXlsx(long jobId, ResultSet resultSet, Path filePath) throws SQLException, IOException {
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        try {
            ResultSetMetaData metaData = resultSet.getMetaData();
            int columnCount = metaData.getColumnCount();
            int sheetIndex = 1;
            SXSSFSheet sheet = workbook.createSheet("result-" + sheetIndex);
            int rowIndex = writeXlsxHeader(sheet, metaData, columnCount);
            long rows = 0;
            while (resultSet.next()) {
                if (rowIndex >= 1_048_576) {
                    sheetIndex++;
                    sheet = workbook.createSheet("result-" + sheetIndex);
                    rowIndex = writeXlsxHeader(sheet, metaData, columnCount);
                }
                Row row = sheet.createRow(rowIndex++);
                for (int i = 1; i <= columnCount; i++) {
                    Cell cell = row.createCell(i - 1);
                    Object value = resultSet.getObject(i);
                    cell.setCellValue(value == null ? "" : String.valueOf(value));
                }
                rows++;
                updateProgress(jobId, rows);
            }
            try (FileOutputStream outputStream = new FileOutputStream(filePath.toFile())) {
                workbook.write(outputStream);
            }
            return rows;
        } finally {
            workbook.dispose();
            workbook.close();
        }
    }

    private int writeXlsxHeader(SXSSFSheet sheet, ResultSetMetaData metaData, int columnCount) throws SQLException {
        Row header = sheet.createRow(0);
        for (int i = 1; i <= columnCount; i++) {
            header.createCell(i - 1).setCellValue(metaData.getColumnLabel(i));
        }
        return 1;
    }

    private void markRunning(long jobId) {
        jdbcTemplate.update(
                "UPDATE " + TABLE_NAME + " SET status = ?, started_at = ?, message = ? WHERE id = ?",
                "RUNNING",
                Timestamp.valueOf(LocalDateTime.now()),
                "导出中",
                jobId
        );
    }

    private void updateProgress(long jobId, long rows) {
        if (rows % PROGRESS_UPDATE_INTERVAL != 0) {
            return;
        }
        jdbcTemplate.update(
                "UPDATE " + TABLE_NAME + " SET exported_rows = ? WHERE id = ?",
                rows,
                jobId
        );
    }

    private ExportJobRecord loadRecord(long jobId) {
        return jdbcTemplate.queryForObject(
                "SELECT id, source_type, format, schema_name, table_name, sql_text, filters_json, sorts_json FROM " + TABLE_NAME + " WHERE id = ?",
                (resultSet, rowNum) -> {
                    ExportJobRecord record = new ExportJobRecord();
                    record.id = resultSet.getLong("id");
                    record.sourceType = resultSet.getString("source_type");
                    record.format = resultSet.getString("format");
                    record.schema = resultSet.getString("schema_name");
                    record.table = resultSet.getString("table_name");
                    record.sql = resultSet.getString("sql_text");
                    String filtersJson = resultSet.getString("filters_json");
                    String sortsJson = resultSet.getString("sorts_json");
                    record.filters = filtersJson == null || filtersJson.trim().isEmpty()
                            ? Collections.<MysqlTableFilterRequest>emptyList()
                            : JSON.parseArray(filtersJson, MysqlTableFilterRequest.class);
                    record.sorts = sortsJson == null || sortsJson.trim().isEmpty()
                            ? Collections.<MysqlTableSortRequest>emptyList()
                            : JSON.parseArray(sortsJson, MysqlTableSortRequest.class);
                    return record;
                },
                jobId
        );
    }

    private MysqlExportJobView toView(ResultSet resultSet) throws SQLException {
        MysqlExportJobView view = new MysqlExportJobView();
        view.setId(resultSet.getLong("id"));
        view.setSourceType(resultSet.getString("source_type"));
        view.setFormat(resultSet.getString("format"));
        view.setSchemaName(resultSet.getString("schema_name"));
        view.setTableName(resultSet.getString("table_name"));
        view.setSqlPreview(buildPreview(resultSet.getString("sql_text")));
        view.setStatus(resultSet.getString("status"));
        view.setFileName(resultSet.getString("file_name"));
        long fileSize = resultSet.getLong("file_size");
        view.setFileSize(resultSet.wasNull() ? null : fileSize);
        long totalRows = resultSet.getLong("total_rows");
        view.setTotalRows(resultSet.wasNull() ? null : totalRows);
        view.setExportedRows(resultSet.getLong("exported_rows"));
        view.setMessage(resultSet.getString("message"));
        view.setCreatedBy(resultSet.getString("created_by"));
        view.setCreatedAt(toLocalDateTime(resultSet.getTimestamp("created_at")));
        view.setStartedAt(toLocalDateTime(resultSet.getTimestamp("started_at")));
        view.setFinishedAt(toLocalDateTime(resultSet.getTimestamp("finished_at")));
        return view;
    }

    private ExportRequest normalizeRequest(MysqlExportCreateRequest request) {
        if (request == null) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "导出参数不能为空");
        }
        ExportRequest normalized = new ExportRequest();
        normalized.sourceType = normalizeUpper(request.getSourceType(), "TABLE");
        normalized.format = normalizeUpper(request.getFormat(), "CSV");
        normalized.schema = blankToNull(request.getSchema());
        normalized.table = blankToNull(request.getTable());
        normalized.sql = blankToNull(request.getSql());
        normalized.filters = request.getFilters() == null ? Collections.<MysqlTableFilterRequest>emptyList() : request.getFilters();
        normalized.sorts = request.getSorts() == null ? Collections.<MysqlTableSortRequest>emptyList() : request.getSorts();

        if (!"TABLE".equals(normalized.sourceType) && !"SQL".equals(normalized.sourceType)) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "不支持的导出来源");
        }
        if (!"CSV".equals(normalized.format) && !"XLSX".equals(normalized.format) && !"SQL".equals(normalized.format)) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "不支持的导出格式");
        }
        if ("TABLE".equals(normalized.sourceType)) {
            MysqlIdentifierUtils.validateIdentifier(normalized.schema, "schema 名称不合法");
            MysqlIdentifierUtils.validateIdentifier(normalized.table, "表名不合法");
        } else if (normalized.sql == null || normalized.sql.trim().isEmpty()) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "导出 SQL 不能为空");
        }
        if ("SQL".equals(normalized.format) && !"TABLE".equals(normalized.sourceType)) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "SQL 格式导出仅支持表数据");
        }
        return normalized;
    }

    private void bindParameters(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (int i = 0; i < parameters.size(); i++) {
            statement.setObject(i + 1, parameters.get(i));
        }
    }

    private void applyStreamingFetchSize(PreparedStatement statement) {
        try {
            statement.setFetchSize(Integer.MIN_VALUE);
        } catch (SQLException ignored) {
            try {
                statement.setFetchSize(1000);
            } catch (SQLException ignoredAgain) {
            }
        }
    }

    private Path resolveExportDirectory() {
        String configured = properties.getExport().getDir();
        if (configured == null || configured.trim().isEmpty()) {
            return Paths.get(System.getProperty("java.io.tmpdir"), "mysql-workbench-exports");
        }
        return Paths.get(configured);
    }

    private String buildFileName(ExportJobRecord record) {
        String base = "TABLE".equals(record.sourceType)
                ? record.schema + "." + record.table
                : "query";
        String suffix = record.format.toLowerCase(Locale.ROOT);
        return sanitizeFileName(base) + "-" + record.id + "-" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now()) + "." + suffix.toLowerCase(Locale.ROOT);
    }

    private String csvCell(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private String sqlLiteral(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || value instanceof BigDecimal) {
            return String.valueOf(value);
        }
        String text = String.valueOf(value)
                .replace("\\", "\\\\")
                .replace("'", "\\'");
        return "'" + text + "'";
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String normalizeUpper(String value, String defaultValue) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? defaultValue : normalized.toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String buildPreview(String sql) {
        if (sql == null) {
            return "";
        }
        String preview = sql.replaceAll("\\s+", " ").trim();
        return preview.length() > 160 ? preview.substring(0, 160) + "..." : preview;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private long jobId(ExportJobRecord record) {
        return record.id;
    }

    private static class ExportRequest {

        String sourceType;

        String format;

        String schema;

        String table;

        String sql;

        List<MysqlTableFilterRequest> filters;

        List<MysqlTableSortRequest> sorts;
    }

    private static class ExportJobRecord extends ExportRequest {

        long id;
    }
}
