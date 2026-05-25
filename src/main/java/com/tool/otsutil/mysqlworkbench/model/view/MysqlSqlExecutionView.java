package com.tool.otsutil.mysqlworkbench.model.view;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MysqlSqlExecutionView {

    private String id;

    private String status;

    private String message;

    private MysqlSqlBatchResultView result;

    private LocalDateTime createdAt;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
