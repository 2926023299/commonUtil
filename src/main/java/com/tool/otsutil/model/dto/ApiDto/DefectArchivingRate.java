package com.tool.otsutil.model.dto.ApiDto;

import lombok.Data;

import java.math.BigDecimal;

// 自动化缺陷消除归档率
@Data
public class DefectArchivingRate {
    private BigDecimal rate;
    private String areaId;
    private String areaName;
    private String areaAliasName;
    private String parentAreaId;
    private String areaCode;
    private String time;
    private Integer count;
    private Integer totalCount;
}
