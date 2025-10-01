package com.tool.otsutil.model.dto.ApiDto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;


@Data
@AllArgsConstructor
public class RateResult {
    private String areaId;
    private String areaName;
    private BigDecimal rate;

	public static RateResult createDefault(String areaId, String areaName) {
		return new RateResult(areaId, areaName, BigDecimal.ZERO);
	}
}
