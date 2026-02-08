package com.tool.otsutil.model.entity;

import java.math.BigDecimal;

import org.apache.poi.hpsf.Decimal;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ies_ms.connectivitynode_b")
public class ConnectivitynodeB {
    private BigDecimal Equipment_ID;
    private BigDecimal Sequence;
    private BigDecimal ID;
    private String Name;
    private String AliasName;
    private String Description;
    private String TerminalRdfID;
    private BigDecimal Substation_ID;
    private BigDecimal VoltageLevel_ID;
    private BigDecimal MultSrcFlag;
    private BigDecimal PhaseCode;
    private BigDecimal DeleteFlag;
}
