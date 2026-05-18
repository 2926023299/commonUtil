package com.tool.otsutil.mysqlworkbench.model.request;

import lombok.Data;

import java.util.List;

@Data
public class MysqlExportCreateRequest {

    private String sourceType;

    private String format;

    private String schema;

    private String table;

    private String sql;

    private Boolean confirmed;

    private List<MysqlTableSortRequest> sorts;

    private List<MysqlTableFilterRequest> filters;
}
