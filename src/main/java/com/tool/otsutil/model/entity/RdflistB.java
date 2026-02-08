package com.tool.otsutil.model.entity;

import java.math.BigDecimal;

import org.apache.poi.hpsf.Decimal;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ies_ms.rdflist_b")
public class RdflistB {
    private BigDecimal DeviceID;
    private String GISRdfID;
    private String EMSRdfID;
    private String PSRType;
    private String AssetID;
    private BigDecimal BJLX;
    private BigDecimal BJID;
    private String wg_device_id;
}
