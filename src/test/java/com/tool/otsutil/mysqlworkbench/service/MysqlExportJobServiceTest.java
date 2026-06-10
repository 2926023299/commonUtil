package com.tool.otsutil.mysqlworkbench.service;

import com.tool.otsutil.mysqlworkbench.config.MysqlWorkbenchProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class MysqlExportJobServiceTest {

    @Test
    void shouldExportSqlDumpWithoutUseStatementAndWithGroupedInsertValues() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MysqlWorkbenchService mysqlWorkbenchService = mock(MysqlWorkbenchService.class);
        MysqlExportJobService service = new MysqlExportJobService(
                jdbcTemplate,
                mysqlWorkbenchService,
                new MysqlWorkbenchProperties()
        );
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);
        Path filePath = Files.createTempFile("mysql-export-job-", ".sql");

        try {
            given(mysqlWorkbenchService.getTableDdl("ops_db", "alarm_log"))
                    .willReturn("CREATE TABLE `alarm_log` (\n  `id` bigint NOT NULL\n) ENGINE=InnoDB");
            given(resultSet.getMetaData()).willReturn(metaData);
            given(metaData.getColumnCount()).willReturn(1);
            given(metaData.getColumnLabel(1)).willReturn("id");
            given(resultSet.next()).willReturn(true, true, false);
            given(resultSet.getObject(1)).willReturn(7L, 8L);

            invokeWriteSql(service, newExportJobRecord("ops_db", "alarm_log"), resultSet, filePath);

            String dumpSql = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8).replace("\r\n", "\n");
            assertFalse(dumpSql.contains("CREATE DATABASE"));
            assertFalse(dumpSql.contains("USE `ops_db`;"));
            assertTrue(dumpSql.contains("DROP TABLE IF EXISTS `alarm_log`;"));
            assertTrue(dumpSql.contains("CREATE TABLE `alarm_log` ("));
            assertTrue(dumpSql.contains("INSERT INTO `alarm_log` (`id`) VALUES\n(7),\n(8);"));
            assertFalse(dumpSql.contains("`ops_db`.`alarm_log`"));
        } finally {
            Files.deleteIfExists(filePath);
            service.shutdown();
        }
    }

    @Test
    void shouldExportSqlDumpWithBatchedInsertValues() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MysqlWorkbenchService mysqlWorkbenchService = mock(MysqlWorkbenchService.class);
        MysqlWorkbenchProperties props = new MysqlWorkbenchProperties();
        props.getExport().setSqlBatchSize(3);
        MysqlExportJobService service = new MysqlExportJobService(jdbcTemplate, mysqlWorkbenchService, props);
        ResultSet resultSet = mock(ResultSet.class);
        ResultSetMetaData metaData = mock(ResultSetMetaData.class);
        Path filePath = Files.createTempFile("mysql-export-job-batch-", ".sql");

        try {
            given(mysqlWorkbenchService.getTableDdl("ops_db", "alarm_log"))
                    .willReturn("CREATE TABLE `alarm_log` (\n  `id` bigint NOT NULL\n) ENGINE=InnoDB");
            given(resultSet.getMetaData()).willReturn(metaData);
            given(metaData.getColumnCount()).willReturn(1);
            given(metaData.getColumnLabel(1)).willReturn("id");
            given(resultSet.next()).willReturn(true, true, true, true, true, false);
            given(resultSet.getObject(1)).willReturn(1L, 2L, 3L, 4L, 5L);

            invokeWriteSql(service, newExportJobRecord("ops_db", "alarm_log"), resultSet, filePath);

            String dumpSql = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8).replace("\r\n", "\n");
            // 第一批 3 行，第二批 2 行 → 两个 INSERT 语句
            assertTrue(dumpSql.contains("INSERT INTO `alarm_log` (`id`) VALUES\n(1),\n(2),\n(3);"));
            assertTrue(dumpSql.contains("INSERT INTO `alarm_log` (`id`) VALUES\n(4),\n(5);"));
        } finally {
            Files.deleteIfExists(filePath);
            service.shutdown();
        }
    }

    private Object newExportJobRecord(String schema, String table) throws Exception {
        Class<?> recordClass = Class.forName("com.tool.otsutil.mysqlworkbench.service.MysqlExportJobService$ExportJobRecord");
        Constructor<?> constructor = recordClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object record = constructor.newInstance();

        setField(recordClass, record, "id", 11L);
        Class<?> requestClass = recordClass.getSuperclass();
        setField(requestClass, record, "sourceType", "TABLE");
        setField(requestClass, record, "format", "SQL");
        setField(requestClass, record, "schema", schema);
        setField(requestClass, record, "table", table);
        return record;
    }

    private void invokeWriteSql(MysqlExportJobService service, Object record, ResultSet resultSet, Path filePath) throws Exception {
        Method method = MysqlExportJobService.class.getDeclaredMethod(
                "writeSql",
                long.class,
                record.getClass(),
                ResultSet.class,
                Path.class
        );
        method.setAccessible(true);
        method.invoke(service, 11L, record, resultSet, filePath);
    }

    private void setField(Class<?> type, Object target, String fieldName, Object value) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
