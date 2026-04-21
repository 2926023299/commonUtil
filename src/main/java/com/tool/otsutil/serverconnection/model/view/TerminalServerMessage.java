package com.tool.otsutil.serverconnection.model.view;

import lombok.Data;

@Data
public class TerminalServerMessage {
    private String type;
    private String data;
    private String status;
    private String message;
    private String path;

    public static TerminalServerMessage output(String data) {
        TerminalServerMessage message = new TerminalServerMessage();
        message.setType("output");
        message.setData(data);
        return message;
    }

    public static TerminalServerMessage status(String status, String messageText) {
        TerminalServerMessage message = new TerminalServerMessage();
        message.setType("status");
        message.setStatus(status);
        message.setMessage(messageText);
        return message;
    }

    public static TerminalServerMessage cwd(String pathValue) {
        TerminalServerMessage message = new TerminalServerMessage();
        message.setType("cwd");
        message.setPath(pathValue);
        return message;
    }
}
