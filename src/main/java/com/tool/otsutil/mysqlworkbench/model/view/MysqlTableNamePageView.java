package com.tool.otsutil.mysqlworkbench.model.view;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MysqlTableNamePageView {

    private String schema;

    private String keyword;

    private Integer page;

    private Integer pageSize;

    private Boolean hasNext;

    private List<String> items = new ArrayList<String>();
}
