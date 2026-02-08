package com.tool.otsutil.model.entity;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("ies_ms.newconnectivitynode_b")
public class NewConnectivitynodeB {

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
