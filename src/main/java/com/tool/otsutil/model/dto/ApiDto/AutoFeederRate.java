package com.tool.otsutil.model.dto.ApiDto;


import lombok.Data;

import java.math.BigDecimal;

@Data
public class AutoFeederRate {
    private BigDecimal rate;
    private String areaId;
    private String areaName;
    private String areaAliasName;
    private String parentAreaId;
    private String areaCode;
    private String time;
    private Integer count;
    private Integer totalCount;
    private Integer supplyArea;
}
