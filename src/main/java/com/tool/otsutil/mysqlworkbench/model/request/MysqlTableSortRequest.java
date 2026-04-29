package com.tool.otsutil.mysqlworkbench.model.request;

import lombok.Data;

@Data
public class MysqlTableSortRequest {

    private String column;

    private String direction;
}
