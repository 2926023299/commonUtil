package com.tool.otsutil.serverconnection.model.request;

import lombok.Data;

@Data
public class OpenTerminalSessionRequest {
    private String serverKey;
    private String initialPath;
}
