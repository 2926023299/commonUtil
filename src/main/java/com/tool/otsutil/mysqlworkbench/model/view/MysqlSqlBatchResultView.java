package com.tool.otsutil.mysqlworkbench.model.view;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MysqlSqlBatchResultView {

    private Long batchId;

    private String schema;

    private Boolean dangerous;

    private Boolean success;

    private String status;

    private String message;

    private Integer failedStatementIndex;

    private Integer statementCount;

    private List<MysqlSqlStatementResultView> results = new ArrayList<MysqlSqlStatementResultView>();
}
