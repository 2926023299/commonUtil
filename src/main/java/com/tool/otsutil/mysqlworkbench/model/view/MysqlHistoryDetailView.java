package com.tool.otsutil.mysqlworkbench.model.view;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class MysqlHistoryDetailView {

    private Long id;

    private String schemaName;

    private String executedBy;

    private String status;

    private Boolean dangerous;

    private String batchText;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private List<MysqlHistoryStatementView> statements = new ArrayList<MysqlHistoryStatementView>();
}
