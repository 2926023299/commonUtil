package com.tool.otsutil.serverconnection.model.request;

import lombok.Data;

@Data
public class DeleteRemoteFileRequest {
    private String path;
    private Boolean recursive;
}
