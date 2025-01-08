package com.tool.otsutil.model.dto.ApiDto;

import lombok.Data;

import java.math.BigDecimal;


// 终端遥控成功率
@Data
public class RemoteSuccessRate {
    private BigDecimal rate;
    private String areaId;
    private String areaName;
    private String areaAliasName;
    private String parentAreaId;
    private String areaCode;
    private String time;
    private String timeDimension;
    private Integer successCount;
    private Integer failCount;
    private Integer totalCount;
    private String updateDate;
}
