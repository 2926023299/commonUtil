package com.tool.otsutil.mysqlworkbench.model.request;

import lombok.Data;

@Data
public class MysqlTableFilterRequest {

    private String column;

    private String operator;

    private Object value;
}
