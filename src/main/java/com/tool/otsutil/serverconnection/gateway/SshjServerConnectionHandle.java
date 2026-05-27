package com.tool.otsutil.serverconnection.gateway;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;

import java.io.IOException;

public class SshjServerConnectionHandle implements ServerConnectionHandle {

    private final String serverKey;
    private final SSHClient sshClient;
    private volatile SFTPClient cachedSftpClient;

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

    public synchronized SFTPClient getSftpClient() throws IOException {
        if (cachedSftpClient == null) {
            cachedSftpClient = sshClient.newSFTPClient();
        }
        return cachedSftpClient;
    }

    @Override
    public void close() throws IOException {
        try {
            if (cachedSftpClient != null) {
                cachedSftpClient.close();
                cachedSftpClient = null;
            }
        } finally {
            sshClient.close();
        }
    }
}
