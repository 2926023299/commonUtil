package com.tool.otsutil.mysqlworkbench.model.view;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Data
public class MysqlSqlStatementResultView {

    private Integer index;

    private String sql;

    private String type;

    private Boolean success;

    private Boolean dangerous;

    private String message;

    private Long durationMs;

    private Long affectedRows;

    private List<String> columns = new ArrayList<String>();

    private List<LinkedHashMap<String, Object>> rows = new ArrayList<LinkedHashMap<String, Object>>();
}
