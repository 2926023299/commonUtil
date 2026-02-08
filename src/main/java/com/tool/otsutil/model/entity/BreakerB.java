package com.tool.otsutil.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("ies_ms.breaker_b")
public class BreakerB {
    private String id;
    private String name;
    private String feeder_id;
    // 其他字段根据实际表结构添加
}