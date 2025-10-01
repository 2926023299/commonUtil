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

    /**
     * 创建一个带有默认值的 IndicatorStatisticRate 对象
     * @return IndicatorStatisticRate 对象，所有数值字段默认为 0.0，字符串字段默认为 ""
     */
    public static IndicatorStatisticRate createDefault() {
        IndicatorStatisticRate defaultInstance = new IndicatorStatisticRate();
        
        defaultInstance.areaId = "";
        defaultInstance.areaName = "";
        defaultInstance.time = "";
        defaultInstance.areaAliasName = "";
        
        BigDecimal zero = BigDecimal.ZERO;
        defaultInstance.remoteSuccessRate = zero;
        defaultInstance.remoteUseRate = zero;
        defaultInstance.rtuOnlineRate = zero;
        defaultInstance.remoteCorrectRate = zero;
        defaultInstance.faCoverageRate = zero;
        defaultInstance.contactEquipmentControllableRate = zero;
        defaultInstance.backboneBreakerSanyaoCovRate = zero;
        defaultInstance.breakerSanyaoCovRate = zero;
        defaultInstance.backupContactEquipmentControllableRate = zero;
        defaultInstance.feederCompletionRate = zero;
        defaultInstance.realfeederCompletionRate = zero;
        defaultInstance.feederCoverageRate = zero;
        defaultInstance.terminalSourceSpecification = zero;
        defaultInstance.landLossWarningCorrectRate = zero;
        defaultInstance.terminalESApplicationRate = zero;
        defaultInstance.lowVoltageMapMappingRate = zero;
        defaultInstance.faSuccessRate = zero;
        defaultInstance.activeColletionMonitorRate = zero;
        defaultInstance.boundarySwitchCovRate = zero;
        defaultInstance.distPowerMonitorRate = zero;
        defaultInstance.fusionTerminalCoverageTaiquRate = zero;
        defaultInstance.boundarySwitchLxInputRate = zero;
        defaultInstance.ctBoundarySwitchLxInputRate = zero;
        defaultInstance.photovoltaicFlexibleControllableRate = zero;
        defaultInstance.chongDianZhanFusionTerminalCoverageTaiquRate = zero;
        defaultInstance.chongDianZhuangFusionTerminalCoverageTaiquRate = zero;
        defaultInstance.chongDianZhuangMonitoredRate = zero;
        defaultInstance.chongDianZhuangControllableRate = zero;
        
        return defaultInstance;
    }
}
