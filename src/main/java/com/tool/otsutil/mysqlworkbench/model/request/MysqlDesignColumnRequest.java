package com.tool.otsutil.mysqlworkbench.model.request;

import lombok.Data;

@Data
public class MysqlDesignColumnRequest {

    private String name;

    private String type;

    private Integer length;

    private Integer scale;

    private Boolean nullable;

    private Boolean autoIncrement;

    private Boolean defaultValuePresent;

    private String defaultValue;

    private String comment;

    private Boolean primaryKey;
}
