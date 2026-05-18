package com.tool.otsutil.mysqlworkbench.model.view;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MysqlExportJobView {

    private Long id;

    private String sourceType;

    private String format;

    private String schemaName;

    private String tableName;

    private String sqlPreview;

    private String status;

    private String fileName;

    private Long fileSize;

    private Long totalRows;

    private Long exportedRows;

    private String message;

    private String createdBy;

    private LocalDateTime createdAt;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
