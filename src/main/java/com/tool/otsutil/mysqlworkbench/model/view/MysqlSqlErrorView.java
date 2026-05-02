package com.tool.otsutil.mysqlworkbench.model.view;

import lombok.Data;

@Data
public class MysqlSqlErrorView {

    private Integer errorCode;

    private String sqlState;

    private String category;

    private String title;

    private String detail;
}
