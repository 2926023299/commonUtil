package com.tool.otsutil.mysqlworkbench.model.view;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Data
public class MysqlTableDataPageView {

    private String schema;

    private String table;

    private Integer page;

    private Integer pageSize;

    private Long total;

    private Boolean readOnly;

    private String readOnlyReason;

    private List<String> keyColumns = new ArrayList<String>();

    private List<LinkedHashMap<String, Object>> rows = new ArrayList<LinkedHashMap<String, Object>>();
}
