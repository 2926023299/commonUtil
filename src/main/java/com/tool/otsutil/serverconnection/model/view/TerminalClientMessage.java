package com.tool.otsutil.serverconnection.model.view;

import lombok.Data;

@Data
public class TerminalClientMessage {
    private String type;
    private String data;
    private Integer cols;
    private Integer rows;
}
