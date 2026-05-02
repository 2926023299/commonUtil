package com.tool.otsutil.mysqlworkbench.model.request;

import lombok.Data;

@Data
public class MysqlSavedQuerySaveRequest {

    private Long id;

    private String title;

    private String schemaName;

    private String sqlText;
}
