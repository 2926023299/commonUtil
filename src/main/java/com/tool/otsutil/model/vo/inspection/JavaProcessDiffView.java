package com.tool.otsutil.model.vo.inspection;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class JavaProcessDiffView {
    private List<String> addedProcesses = new ArrayList<String>();
    private List<String> removedProcesses = new ArrayList<String>();
    private List<String> unchangedProcesses = new ArrayList<String>();
}
