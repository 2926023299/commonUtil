package com.tool.otsutil.model.dto.inspection;

import lombok.Data;

@Data
public class ServerInspectionListRequest {
    private String ip;
    private String date;
    private Integer status;
    private Integer page = 1;
    private Integer pageSize = 10;
}
