package com.tool.otsutil.mysqlworkbench.service;

import com.tool.otsutil.exception.CustomException;
import com.tool.otsutil.model.common.AppHttpCodeEnum;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlSqlExecuteRequest;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlTableQueryRequest;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlSqlBatchResultView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlSqlStatementResultView;
import com.tool.otsutil.mysqlworkbench.model.view.MysqlTableDataPageView;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MysqlWorkbenchServiceTest {

    @Test
    void shouldRejectMissingTableWhenLoadingDdl() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MysqlHistoryService mysqlHistoryService = mock(MysqlHistoryService.class);
        MysqlWorkbenchService service = new MysqlWorkbenchService(jdbcTemplate, mysqlHistoryService);

        given(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ? AND table_type = 'BASE TABLE'"),
                eq(Long.class),
                eq("ies_ls"),
                eq("missing_table")
        )).willReturn(0L);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.getTableDdl("ies_ls", "missing_table")
        );

        assertEquals(AppHttpCodeEnum.DATA_NOT_EXIST, exception.getAppHttpCodeEnum());
        assertEquals("表不存在: ies_ls.missing_table", exception.getMessage());
    }

    @Test
    void shouldRejectMissingSchemaBeforeExecutingSql() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MysqlHistoryService mysqlHistoryService = mock(MysqlHistoryService.class);
        MysqlWorkbenchService service = new MysqlWorkbenchService(jdbcTemplate, mysqlHistoryService);
        MysqlSqlExecuteRequest request = new MysqlSqlExecuteRequest();
        request.setSchema("missing_schema");
        request.setSql("select 1;");

        given(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?"),
                eq(Long.class),
                eq("missing_schema")
        )).willReturn(0L);

        CustomException exception = assertThrows(
                CustomException.class,
                () -> service.executeSql(request, "tester")
        );

        assertEquals(AppHttpCodeEnum.DATA_NOT_EXIST, exception.getAppHttpCodeEnum());
        assertEquals("schema 不存在: missing_schema", exception.getMessage());
    }

    @Test
    void shouldCapSqlResultsAndContinueCountingFullResultSet() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MysqlHistoryService mysqlHistoryService = mock(MysqlHistoryService.class);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);
        MysqlWorkbenchService service = new MysqlWorkbenchService(jdbcTemplate, mysqlHistoryService);

        MysqlSqlExecuteRequest request = new MysqlSqlExecuteRequest();
        request.setSql("select * from huge_table");
        request.setMaxDisplayRows(2);

        given(mysqlHistoryService.startBatch(any(), eq(request.getSql()), anyInt(), anyBoolean(), eq("tester"), any(LocalDateTime.class)))
                .willReturn(123L);
        given(jdbcTemplate.getDataSource()).willReturn(dataSource);
        given(dataSource.getConnection()).willReturn(connection);
        given(connection.createStatement()).willReturn(statement);
        given(statement.execute(request.getSql())).willReturn(true);
        given(statement.getResultSet()).willReturn(resultSet);
        given(resultSet.getMetaData()).willReturn(metaData);
        given(metaData.getColumnCount()).willReturn(1);
        given(metaData.getColumnLabel(1)).willReturn("id");
        given(resultSet.next()).willReturn(true, true, true, false);
        given(resultSet.getObject(1)).willReturn(1, 2);

        MysqlSqlBatchResultView result = service.executeSql(request, "tester");
        MysqlSqlStatementResultView statementResult = result.getResults().get(0);

        assertEquals(2, statementResult.getRows().size());
        assertEquals(2, statementResult.getDisplayRowCount());
        assertEquals(2, statementResult.getDisplayLimit());
        assertTrue(statementResult.getTruncated());
        assertEquals(3L, statementResult.getTotalRowCount());
        verify(statement, never()).setMaxRows(anyInt());
    }

    @Test
    void shouldReturnTotalRowCountWhenSqlResultIsFullyRead() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MysqlHistoryService mysqlHistoryService = mock(MysqlHistoryService.class);
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);
        MysqlWorkbenchService service = new MysqlWorkbenchService(jdbcTemplate, mysqlHistoryService);

        MysqlSqlExecuteRequest request = new MysqlSqlExecuteRequest();
        request.setSql("select * from small_table");
        request.setMaxDisplayRows(5);

        given(mysqlHistoryService.startBatch(any(), eq(request.getSql()), anyInt(), anyBoolean(), eq("tester"), any(LocalDateTime.class)))
                .willReturn(124L);
        given(jdbcTemplate.getDataSource()).willReturn(dataSource);
        given(dataSource.getConnection()).willReturn(connection);
        given(connection.createStatement()).willReturn(statement);
        given(statement.execute(request.getSql())).willReturn(true);
        given(statement.getResultSet()).willReturn(resultSet);
        given(resultSet.getMetaData()).willReturn(metaData);
        given(metaData.getColumnCount()).willReturn(1);
        given(metaData.getColumnLabel(1)).willReturn("id");
        given(resultSet.next()).willReturn(true, true, false);
        given(resultSet.getObject(1)).willReturn(1, 2);

        MysqlSqlBatchResultView result = service.executeSql(request, "tester");
        MysqlSqlStatementResultView statementResult = result.getResults().get(0);

        assertEquals(2, statementResult.getRows().size());
        assertEquals(2, statementResult.getDisplayRowCount());
        assertEquals(2L, statementResult.getTotalRowCount());
        assertFalse(statementResult.getTruncated());
        verify(statement, never()).setMaxRows(anyInt());
    }

    @Test
    void shouldQueryTableDataWithoutCountingByDefault() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MysqlHistoryService mysqlHistoryService = mock(MysqlHistoryService.class);
        MysqlWorkbenchService service = new MysqlWorkbenchService(jdbcTemplate, mysqlHistoryService);
        MysqlTableQueryRequest request = new MysqlTableQueryRequest();
        request.setSchema("ies_ls");
        request.setTable("inspection_table");
        request.setPage(1);
        request.setPageSize(2);

        given(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ? AND table_type = 'BASE TABLE'"),
                eq(Long.class),
                eq("ies_ls"),
                eq("inspection_table")
        )).willReturn(1L);
        given(jdbcTemplate.queryForMap(
                eq("SELECT engine, table_collation, table_comment FROM information_schema.tables WHERE table_schema = ? AND table_name = ? AND table_type = 'BASE TABLE'"),
                eq("ies_ls"),
                eq("inspection_table")
        )).willReturn(Collections.singletonMap("engine", "InnoDB"));
        given(jdbcTemplate.queryForList(
                eq("SELECT column_name, data_type, column_type, character_maximum_length, numeric_precision, numeric_scale, is_nullable, column_default, column_comment, extra FROM information_schema.columns WHERE table_schema = ? AND table_name = ? ORDER BY ordinal_position"),
                eq("ies_ls"),
                eq("inspection_table")
        )).willReturn(Collections.singletonList(columnRow("id")));
        given(jdbcTemplate.queryForList(
                eq("SELECT index_name, non_unique, column_name, seq_in_index FROM information_schema.statistics WHERE table_schema = ? AND table_name = ? ORDER BY index_name, seq_in_index"),
                eq("ies_ls"),
                eq("inspection_table")
        )).willReturn(Collections.singletonList(primaryIndexRow("id")));
        given(jdbcTemplate.queryForList(
                eq("SELECT * FROM `ies_ls`.`inspection_table` LIMIT ?, ?"),
                eq(0),
                eq(3)
        )).willReturn(Arrays.asList(row("id", 1), row("id", 2), row("id", 3)));

        MysqlTableDataPageView page = service.queryTableData(request);

        assertEquals(2, page.getRows().size());
        assertTrue(page.getHasNext());
        assertNull(page.getTotal());
        verify(jdbcTemplate, never()).queryForObject(
                eq("SELECT COUNT(*) FROM `ies_ls`.`inspection_table`"),
                any(Object[].class),
                eq(Long.class)
        );
    }

    @Test
    void shouldListTablesBySchemaWithKeywordAndPagination() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MysqlHistoryService mysqlHistoryService = mock(MysqlHistoryService.class);
        MysqlWorkbenchService service = new MysqlWorkbenchService(jdbcTemplate, mysqlHistoryService);

        given(jdbcTemplate.queryForObject(
                eq("SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name = ?"),
                eq(Long.class),
                eq("ies_ls")
        )).willReturn(1L);
        given(jdbcTemplate.queryForList(
                eq("SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND table_type = 'BASE TABLE' AND table_name LIKE ? ORDER BY table_name LIMIT ? OFFSET ?"),
                eq(String.class),
                eq("ies_ls"),
                eq("%energy%"),
                eq(50),
                eq(50)
        )).willReturn(Arrays.asList("breaker_energy_data", "energy_log"));

        List<String> tables = service.listTables("ies_ls", 2, 50, "energy");

        assertEquals(Arrays.asList("breaker_energy_data", "energy_log"), tables);
    }

    private Map<String, Object> columnRow(String name) {
        return row("column_name", name,
                "data_type", "bigint",
                "column_type", "bigint",
                "character_maximum_length", null,
                "numeric_precision", 19,
                "numeric_scale", 0,
                "is_nullable", "NO",
                "column_default", null,
                "column_comment", "",
                "extra", "");
    }

    private Map<String, Object> primaryIndexRow(String column) {
        return row("index_name", "PRIMARY",
                "non_unique", 0,
                "column_name", column,
                "seq_in_index", 1);
    }

    private Map<String, Object> row(Object... values) {
        java.util.LinkedHashMap<String, Object> row = new java.util.LinkedHashMap<String, Object>();
        for (int i = 0; i < values.length; i += 2) {
            row.put(String.valueOf(values[i]), values[i + 1]);
        }
        return row;
    }
}
