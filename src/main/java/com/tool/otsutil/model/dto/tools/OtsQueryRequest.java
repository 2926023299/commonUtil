package com.tool.otsutil.model.dto.tools;

import lombok.Data;

@Data
public class OtsQueryRequest {
    private String tableName;
    private String keyName;
    private String startKey;
    private String endKey;
    private Integer limit;
}
