package com.tool.otsutil.mysqlworkbench.model.request;

import lombok.Data;

import java.util.List;

@Data
public class MysqlDesignIndexRequest {

    private String name;

    private Boolean unique;

    private List<String> columns;
}
