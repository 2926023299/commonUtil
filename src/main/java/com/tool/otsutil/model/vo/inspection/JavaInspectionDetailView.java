package com.tool.otsutil.model.vo.inspection;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class JavaInspectionDetailView {
    private String ip;
    private Integer currentStatus;
    private Integer previousStatus;
    private String currentUpdateTime;
    private String previousUpdateTime;
    private String description;
    private List<String> currentProcesses = new ArrayList<String>();
    private List<String> previousProcesses = new ArrayList<String>();
    private JavaProcessDiffView diff;
}
