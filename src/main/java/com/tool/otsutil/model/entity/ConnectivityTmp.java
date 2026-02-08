package com.tool.otsutil.model.entity;

import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("ies_tmp.connectivity_tmp")
public class ConnectivityTmp {

    private BigDecimal Equipment_ID; 
    private String Device_GISRDFID;
    private String Device_EMSRDFID;
    private String TerminalRdfID; // 终端RDFID
    private BigDecimal Sequence; // 连接点端号
    private BigDecimal ID; 
    private String MultSrcFlag; // 多电源标示
    private String ConnectivityPointGISRDFID;
    private String ConnectivityPointEMSRDFID;
}
