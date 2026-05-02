package com.tool.otsutil.mysqlworkbench.service;

import com.tool.otsutil.exception.CustomException;
import com.tool.otsutil.model.common.AppHttpCodeEnum;
import com.tool.otsutil.mysqlworkbench.model.request.MysqlSqlExecuteRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

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
}
