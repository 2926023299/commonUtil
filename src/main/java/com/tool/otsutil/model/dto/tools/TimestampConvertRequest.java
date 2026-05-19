package com.tool.otsutil.model.dto.tools;

import lombok.Data;

@Data
public class TimestampConvertRequest {
    private String value;
    private String unit;
    private String direction;
    private String format;
}
