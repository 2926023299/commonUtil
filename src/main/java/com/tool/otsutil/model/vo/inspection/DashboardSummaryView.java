package com.tool.otsutil.model.vo.inspection;

import lombok.Data;

@Data
public class DashboardSummaryView {
    private StatusSummaryView summary;
    private ResourcePeakView cpuPeak;
    private ResourcePeakView memoryPeak;
    private ResourcePeakView diskPeak;
    private String lastInspectionTime;
    private int javaChangedServerCount;
    private TopologySummaryView topology;
}
