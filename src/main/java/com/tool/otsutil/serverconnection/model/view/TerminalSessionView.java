package com.tool.otsutil.serverconnection.model.view;

import lombok.Data;

@Data
public class TerminalSessionView {
    private String sessionId;
    private String serverKey;
    private String displayName;
    private String username;
    private String cwd;
}
