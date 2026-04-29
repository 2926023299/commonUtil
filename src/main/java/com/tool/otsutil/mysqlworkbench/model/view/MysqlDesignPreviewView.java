package com.tool.otsutil.mysqlworkbench.model.view;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MysqlDesignPreviewView {

    private String schema;

    private String table;

    private Boolean createMode;

    private Boolean dangerous;

    private List<String> statements = new ArrayList<String>();
}
