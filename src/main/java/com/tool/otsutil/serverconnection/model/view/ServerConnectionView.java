package com.tool.otsutil.serverconnection.model.view;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ServerConnectionView {
    private String serverKey;
    private String displayName;
    private String ip;
    private int port;
    private String username;
    private List<String> jars = new ArrayList<String>();
}
