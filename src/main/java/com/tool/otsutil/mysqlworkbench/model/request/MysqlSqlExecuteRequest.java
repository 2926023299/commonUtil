package com.tool.otsutil.mysqlworkbench.model.request;

import lombok.Data;

@Data
public class MysqlSqlExecuteRequest {

    private String schema;

    private String sql;

    private Boolean confirmed;

    private Integer maxDisplayRows;
}
