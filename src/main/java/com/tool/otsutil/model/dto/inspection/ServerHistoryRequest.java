package com.tool.otsutil.model.dto.inspection;

import lombok.Data;

@Data
public class ServerHistoryRequest {
    private String ip;
    private Integer page = 1;
    private Integer pageSize = 10;
}
