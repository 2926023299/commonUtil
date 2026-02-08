package com.tool.otsutil.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 断路器能耗数据实体类
 */
@Data
@TableName("breaker_energy_data")
public class BreakerEnergyData {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 开关标识
     */
    @TableField("breaker_id")
    private String breakerId;

    /**
     * 数据时标
     */
    @TableField("data_time")
    private String dataTime;

    /**
     * 数据类型
     */
    @TableField("data_type")
    private Integer dataType;

    /**
     * 数据值
     */
    @TableField("data_value")
    private Double dataValue;

    /**
     * 地市编码
     */
    @TableField("city_code")
    private Integer cityCode;

    /**
     * 文件时间
     */
    @TableField("file_time")
    private Date fileTime;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private Date updateTime;
}