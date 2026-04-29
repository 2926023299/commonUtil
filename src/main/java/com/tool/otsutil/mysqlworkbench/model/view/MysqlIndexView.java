package com.tool.otsutil.mysqlworkbench.model.view;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MysqlIndexView {

    private String name;

    private Boolean unique;

    private Boolean primaryKey;

    private List<String> columns = new ArrayList<String>();
}
