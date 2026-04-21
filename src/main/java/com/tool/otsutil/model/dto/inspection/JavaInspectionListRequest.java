package com.tool.otsutil.model.dto.inspection;

import lombok.Data;

@Data
public class JavaInspectionListRequest {
    private String ip;
    private String programName;
    private String stability;
    private Integer status;
    private Integer page = 1;
    private Integer pageSize = 10;
}
