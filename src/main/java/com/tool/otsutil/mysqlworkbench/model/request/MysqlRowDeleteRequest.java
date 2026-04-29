package com.tool.otsutil.mysqlworkbench.model.request;

import lombok.Data;

import java.util.LinkedHashMap;

@Data
public class MysqlRowDeleteRequest {

    private String schema;

    private String table;

    private LinkedHashMap<String, Object> keyValues;
}
