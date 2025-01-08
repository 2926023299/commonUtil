package com.tool.otsutil.model.dto.ApiDto;

import lombok.Data;

import java.math.BigDecimal;

// 终端频繁掉线率
@Data
public class RtuRreqUnline {
    private BigDecimal rate;
    private String areaId;
    private String areaName;
    private String areaAliasName;
    private String parentAreaId;
    private String areaCode;
    private String time;
    private Integer freqUnlineCount;
    private Integer totalCount;
}
