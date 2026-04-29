package com.tool.otsutil.mysqlworkbench.model.request;

import lombok.Data;

import java.util.List;

@Data
public class MysqlTableQueryRequest {

    private String schema;

    private String table;

    private Integer page;

    private Integer pageSize;

    private List<MysqlTableSortRequest> sorts;

    private List<MysqlTableFilterRequest> filters;
}
