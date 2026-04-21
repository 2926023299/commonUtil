package com.tool.otsutil.serverconnection.model.request;

import lombok.Data;

@Data
public class RenameRemoteFileRequest {
    private String fromPath;
    private String toPath;
}
