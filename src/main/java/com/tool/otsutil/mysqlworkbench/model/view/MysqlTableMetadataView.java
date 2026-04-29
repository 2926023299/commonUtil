package com.tool.otsutil.mysqlworkbench.model.view;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MysqlTableMetadataView {

    private String schema;

    private String table;

    private String tableComment;

    private String engine;

    private String charset;

    private Boolean readOnly;

    private String readOnlyReason;

    private List<String> keyColumns = new ArrayList<String>();

    private List<MysqlColumnView> columns = new ArrayList<MysqlColumnView>();

    private List<MysqlIndexView> indexes = new ArrayList<MysqlIndexView>();
}
