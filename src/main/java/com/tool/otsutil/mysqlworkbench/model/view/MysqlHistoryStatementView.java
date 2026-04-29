package com.tool.otsutil.mysqlworkbench.model.view;

import lombok.Data;

@Data
public class MysqlHistoryStatementView {

    private Long id;

    private Integer statementOrder;

    private String statementType;

    private Boolean success;

    private Long affectedRows;

    private Integer resultSize;

    private Long durationMs;

    private String errorMessage;

    private String statementText;
}
