package com.tool.otsutil.mysqlworkbench.util;

import com.tool.otsutil.mysqlworkbench.model.view.MysqlSqlErrorView;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MysqlSqlErrorFormatterTest {

    @Test
    void shouldClassifyMissingTableError() {
        SQLException exception = new SQLException("Table 'inspection.missing_table' doesn't exist", "42S02", 1146);

        MysqlSqlErrorView view = MysqlSqlErrorFormatter.format(exception);

        assertEquals("TABLE_NOT_FOUND", view.getCategory());
        assertEquals("表不存在", view.getTitle());
        assertEquals(1146, view.getErrorCode());
        assertEquals("42S02", view.getSqlState());
        assertTrue(MysqlSqlErrorFormatter.toDisplayMessage(exception).contains("表不存在"));
    }

    @Test
    void shouldClassifySyntaxError() {
        SQLException exception = new SQLException("You have an error in your SQL syntax", "42000", 1064);

        MysqlSqlErrorView view = MysqlSqlErrorFormatter.format(exception);

        assertEquals("SYNTAX_ERROR", view.getCategory());
        assertEquals("SQL 语法错误", view.getTitle());
    }
}
