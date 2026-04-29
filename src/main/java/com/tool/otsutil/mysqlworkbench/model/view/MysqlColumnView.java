package com.tool.otsutil.mysqlworkbench.model.view;

import lombok.Data;

@Data
public class MysqlColumnView {

    private String name;

    private String dataType;

    private String columnType;

    private Integer length;

    private Integer scale;

    private Boolean nullable;

    private String defaultValue;

    private String comment;

    private Boolean autoIncrement;

    private Boolean primaryKey;

    private Boolean uniqueKey;
}
