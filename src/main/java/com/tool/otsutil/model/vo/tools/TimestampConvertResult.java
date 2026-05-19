package com.tool.otsutil.model.vo.tools;

import lombok.Data;

@Data
public class TimestampConvertResult {
    private Long timestampSeconds;
    private Long timestampMillis;
    private String iso8601;
    private String localDateTime;
    private String formatted;
}
