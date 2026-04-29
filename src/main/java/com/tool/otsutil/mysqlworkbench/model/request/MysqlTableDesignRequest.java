package com.tool.otsutil.mysqlworkbench.model.request;

import lombok.Data;

import java.util.List;

@Data
public class MysqlTableDesignRequest {

    private String schema;

    private String table;

    private Boolean createMode;

    private String tableComment;

    private String engine;

    private String charset;

    private Boolean confirmed;

    private List<MysqlDesignColumnRequest> columns;

    private List<MysqlDesignIndexRequest> indexes;
}
