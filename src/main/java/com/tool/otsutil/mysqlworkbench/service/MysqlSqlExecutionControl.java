package com.tool.otsutil.mysqlworkbench.service;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class MysqlSqlExecutionControl {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private final AtomicReference<Statement> currentStatement = new AtomicReference<Statement>();

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void cancel() {
        cancelled.set(true);
        cancelStatement(currentStatement.get());
    }

    public void bindStatement(Statement statement) {
        currentStatement.set(statement);
        if (cancelled.get()) {
            cancelStatement(statement);
        }
    }

    public void clearStatement(Statement statement) {
        currentStatement.compareAndSet(statement, null);
    }

    private void cancelStatement(Statement statement) {
        if (statement == null) {
            return;
        }
        try {
            statement.cancel();
        } catch (SQLException ignored) {
        }
    }
}
