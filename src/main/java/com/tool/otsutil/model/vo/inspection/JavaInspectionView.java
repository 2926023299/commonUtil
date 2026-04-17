package com.tool.otsutil.model.vo.inspection;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class JavaInspectionView {
    private String ip;
    private String updateTime;
    private Integer status;
    private String description;
    private List<String> javaProcesses = new ArrayList<String>();
    private int javaProcessCount;
    private boolean hasDiff;
    private List<String> addedProcesses = new ArrayList<String>();
    private List<String> removedProcesses = new ArrayList<String>();
}
