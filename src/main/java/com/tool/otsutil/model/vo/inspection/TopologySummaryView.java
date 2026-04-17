package com.tool.otsutil.model.vo.inspection;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TopologySummaryView {
    private String date;
    private int zyTotal;
    private int dyTotal;
    private List<TopologyCityView> cities = new ArrayList<TopologyCityView>();
}
