package com.tool.otsutil.mysqlworkbench.model.request;

import lombok.Data;

import java.util.LinkedHashMap;

@Data
public class MysqlRowInsertRequest {

    private String schema;

    private String table;

    private LinkedHashMap<String, Object> values;
}
