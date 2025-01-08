package com.tool.otsutil.model.dto.ApiDto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class IndicatorStatisticRate {
    private String areaId;
    private String areaName;
    private String time;
    private String areaAliasName;
    private BigDecimal remoteSuccessRate;
    private BigDecimal remoteUseRate;
    private BigDecimal rtuOnlineRate;
    private BigDecimal remoteCorrectRate;
    private BigDecimal faCoverageRate;  //配电自动化覆盖率
    private BigDecimal contactEquipmentControllableRate; //联络开关可控率
    private BigDecimal backboneBreakerSanyaoCovRate;
    private BigDecimal breakerSanyaoCovRate;
    private BigDecimal backupContactEquipmentControllableRate;
    private BigDecimal feederCompletionRate;
    private BigDecimal realfeederCompletionRate;
    private BigDecimal feederCoverageRate;
    private BigDecimal terminalSourceSpecification;
    private BigDecimal landLossWarningCorrectRate;
    private BigDecimal terminalESApplicationRate;
    private BigDecimal lowVoltageMapMappingRate;
    private BigDecimal faSuccessRate;
    private BigDecimal activeColletionMonitorRate;
    private BigDecimal boundarySwitchCovRate;
    private BigDecimal distPowerMonitorRate;
    private BigDecimal fusionTerminalCoverageTaiquRate;
    private BigDecimal boundarySwitchLxInputRate;
    private BigDecimal ctBoundarySwitchLxInputRate;
    private BigDecimal photovoltaicFlexibleControllableRate;
    private BigDecimal chongDianZhanFusionTerminalCoverageTaiquRate;
    private BigDecimal chongDianZhuangFusionTerminalCoverageTaiquRate;
    private BigDecimal chongDianZhuangMonitoredRate;
    private BigDecimal chongDianZhuangControllableRate;
}
