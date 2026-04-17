package com.tool.otsutil.model.vo.inspection;

import lombok.Data;

@Data
public class ResourcePeakView {
    private String ip;
    private double value;
    private Integer status;
    private String updateTime;
}
