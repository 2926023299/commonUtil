package com.tool.otsutil.model.entity;

import java.math.BigDecimal;

import org.apache.poi.hpsf.Decimal;

import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("ies_ms.noderdflist_b")
public class NodeRdflistB {
    private BigDecimal DeviceID;
    private String GISRdfID;
    private String EMSRdfID;
    private BigDecimal BJID;
}
