package com.tool.otsutil.mysqlworkbench.util;

import com.tool.otsutil.mysqlworkbench.model.view.MysqlSqlErrorView;

import java.sql.SQLException;

public final class MysqlSqlErrorFormatter {

    private MysqlSqlErrorFormatter() {
    }

    public static MysqlSqlErrorView format(SQLException exception) {
        MysqlSqlErrorView view = new MysqlSqlErrorView();
        view.setErrorCode(exception.getErrorCode());
        view.setSqlState(exception.getSQLState());
        view.setCategory(resolveCategory(exception.getErrorCode(), exception.getSQLState()));
        view.setTitle(resolveTitle(exception.getErrorCode(), exception.getSQLState()));
        view.setDetail(exception.getMessage());
        return view;
    }

    public static String toDisplayMessage(SQLException exception) {
        MysqlSqlErrorView view = format(exception);
        StringBuilder builder = new StringBuilder();
        builder.append(view.getTitle());
        if (view.getErrorCode() != null && view.getErrorCode() > 0) {
            builder.append("，错误码 ").append(view.getErrorCode());
        }
        if (view.getSqlState() != null && !view.getSqlState().trim().isEmpty()) {
            builder.append("，SQLState ").append(view.getSqlState());
        }
        return builder.toString();
    }

    private static String resolveCategory(int errorCode, String sqlState) {
        switch (errorCode) {
            case 1049:
                return "UNKNOWN_DATABASE";
            case 1054:
                return "UNKNOWN_COLUMN";
            case 1062:
                return "DUPLICATE_KEY";
            case 1064:
                return "SYNTAX_ERROR";
            case 1142:
                return "PERMISSION_DENIED";
            case 1146:
                return "TABLE_NOT_FOUND";
            case 1048:
                return "NOT_NULL_VIOLATION";
            case 1205:
                return "LOCK_WAIT_TIMEOUT";
            case 1213:
                return "DEADLOCK";
            case 1451:
            case 1452:
                return "FOREIGN_KEY_VIOLATION";
            default:
                if (sqlState != null && sqlState.startsWith("42")) {
                    return "SQL_SYNTAX_OR_OBJECT_ERROR";
                }
                if (sqlState != null && sqlState.startsWith("23")) {
                    return "CONSTRAINT_VIOLATION";
                }
                return "MYSQL_ERROR";
        }
    }

    private static String resolveTitle(int errorCode, String sqlState) {
        switch (errorCode) {
            case 1049:
                return "数据库不存在";
            case 1054:
                return "字段不存在";
            case 1062:
                return "唯一键冲突";
            case 1064:
                return "SQL 语法错误";
            case 1142:
                return "数据库权限不足";
            case 1146:
                return "表不存在";
            case 1048:
                return "字段不允许为空";
            case 1205:
                return "锁等待超时";
            case 1213:
                return "事务死锁";
            case 1451:
            case 1452:
                return "外键约束失败";
            default:
                if (sqlState != null && sqlState.startsWith("42")) {
                    return "SQL 语法或对象错误";
                }
                if (sqlState != null && sqlState.startsWith("23")) {
                    return "数据约束错误";
                }
                return "MySQL 执行错误";
        }
    }
}
