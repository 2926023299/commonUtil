package com.tool.otsutil.mysqlworkbench.model.view;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MysqlSavedQueryView {

    private Long id;

    private String title;

    private String schemaName;

    private String sqlText;

    private String statementPreview;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
