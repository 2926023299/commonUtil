package com.tool.otsutil.mysqlworkbench.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlStatementSplitterTest {

    @Test
    void shouldSplitStatementsOutsideQuotesAndComments() {
        String sql = "SELECT ';' AS semi; -- comment ;\n"
                + "UPDATE inspection.device_status SET remark = 'line;still text' WHERE id = 1;\n"
                + "/* multi ; comment */\n"
                + "DELETE FROM inspection.device_status WHERE id = 2;";

        assertEquals(
                Arrays.asList(
                        "SELECT ';' AS semi",
                        "UPDATE inspection.device_status SET remark = 'line;still text' WHERE id = 1",
                        "DELETE FROM inspection.device_status WHERE id = 2"
                ),
                SqlStatementSplitter.split(sql)
        );
    }

    @Test
    void shouldIgnoreBlankStatements() {
        assertEquals(Collections.emptyList(), SqlStatementSplitter.split(" ; \n ; "));
    }
}
