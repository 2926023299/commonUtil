package com.tool.otsutil.serverconnection.model.request;

import lombok.Data;

@Data
public class CreateDirectoryRequest {
    private String path;
    private String name;
}
