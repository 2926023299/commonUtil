package com.tool.otsutil.model.dto.ApiDto;

import lombok.Data;

import java.math.BigDecimal;

// 智能配电站房覆盖率
@Data
public class IntelDistributeCoverage {
    private BigDecimal rate;
    private String areaId;
    private String areaName;
    private String areaAliasName;
    private String parentAreaId;
    private String areaCode;
    private String time;
    private Integer coverCount;
    private Integer totalCount;
}
