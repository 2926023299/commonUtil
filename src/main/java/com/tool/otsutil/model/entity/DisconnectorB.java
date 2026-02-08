package com.tool.otsutil.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ies_ms.disconnector_b")
public class DisconnectorB {
    private String id;
    private String name;
    private String feeder_id;
    // 其他字段根据实际表结构添加
}