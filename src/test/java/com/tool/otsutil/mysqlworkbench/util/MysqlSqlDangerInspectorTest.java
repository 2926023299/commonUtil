package com.tool.otsutil.mysqlworkbench.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MysqlSqlDangerInspectorTest {

    @Test
    void shouldFlagDropAndAlterStatementsAsDangerous() {
        assertTrue(MysqlSqlDangerInspector.inspect("DROP TABLE inspection.device_status").isDangerous());
        assertTrue(MysqlSqlDangerInspector.inspect("ALTER TABLE inspection.device_status ADD COLUMN operator_name VARCHAR(32)").isDangerous());
    }

    @Test
    void shouldFlagUpdateAndDeleteWithoutWhereAsDangerous() {
        assertTrue(MysqlSqlDangerInspector.inspect("UPDATE inspection.device_status SET status = 'OFFLINE'").isDangerous());
        assertTrue(MysqlSqlDangerInspector.inspect("DELETE FROM inspection.device_status").isDangerous());
    }

    @Test
    void shouldTreatTargetedStatementsAsSafe() {
        assertFalse(MysqlSqlDangerInspector.inspect("UPDATE inspection.device_status SET status = 'ONLINE' WHERE id = 1").isDangerous());
        assertFalse(MysqlSqlDangerInspector.inspect("DELETE FROM inspection.device_status WHERE id = 1").isDangerous());
        assertFalse(MysqlSqlDangerInspector.inspect("SELECT * FROM inspection.device_status").isDangerous());
    }
}
