package com.tool.otsutil.model.vo.inspection;

import lombok.Data;

@Data
public class JavaInspectionSummaryView {
    private int serverCount;
    private int processCount;
    private int changedCount;
}
