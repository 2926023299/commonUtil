package com.tool.otsutil.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ies_ms.feeder_b")
public class FeederB {
    private String id;
    private String name;
    // 其他字段根据实际表结构添加
}