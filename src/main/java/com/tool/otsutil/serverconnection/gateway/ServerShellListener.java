package com.tool.otsutil.serverconnection.gateway;

public interface ServerShellListener {

    void onOutput(String data);

    void onStatus(String status, String message);

    void onCurrentDirectory(String cwd);
}
