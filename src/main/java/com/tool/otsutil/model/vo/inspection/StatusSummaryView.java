package com.tool.otsutil.model.vo.inspection;

import lombok.Data;

@Data
public class StatusSummaryView {
    private int serverCount;
    private int normalCount;
    private int warningCount;
    private int errorCount;
}
