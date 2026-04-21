package com.tool.otsutil.serverconnection.gateway;

import net.schmizz.sshj.SSHClient;

import java.io.IOException;

public class SshjServerConnectionHandle implements ServerConnectionHandle {

    private final String serverKey;
    private final SSHClient sshClient;

    public SshjServerConnectionHandle(String serverKey, SSHClient sshClient) {
        this.serverKey = serverKey;
        this.sshClient = sshClient;
    }

    @Override
    public String getServerKey() {
        return serverKey;
    }

    public SSHClient getSshClient() {
        return sshClient;
    }

    @Override
    public void close() throws IOException {
        sshClient.close();
    }
}
