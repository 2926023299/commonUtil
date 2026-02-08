package com.tool.otsutil.model.entity;

import java.math.BigDecimal;

import com.alicloud.openservices.tablestore.timestream.model.annotation.Field;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
@Data
@Getter
@Setter
@TableName("tmp.qz")
public class ConnectivityForQz {
	@TableField("设备GISRDFID")
	private String deviceGISRDFID;
	@TableField("设备EMSRDFID")
	private String deviceEMSRDFID;
	@TableField("连接点端号")
	private BigDecimal connectivityPointEndID;
	@TableField("多电源标示")
	private BigDecimal multiPowerFlag;
	@TableField( "连接点GISRDFID")
	private String connectivityPointGISRDFID;
	@TableField("连接点EMSRDFID")
	private String connectivityPointEMSRDFID;

}
