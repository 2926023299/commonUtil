package com.tool.otsutil.mysqlworkbench.model.view;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MysqlHistoryBatchView {

    private Long id;

    private String schemaName;

    private String executedBy;

    private String status;

    private Boolean dangerous;

    private Integer statementCount;

    private String statementPreview;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
