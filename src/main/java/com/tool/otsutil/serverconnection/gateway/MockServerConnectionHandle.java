package com.tool.otsutil.serverconnection.gateway;

import java.io.IOException;
import java.nio.file.Path;

public class MockServerConnectionHandle implements ServerConnectionHandle {

    private final String serverKey;
    private final Path rootPath;

    public MockServerConnectionHandle(String serverKey, Path rootPath) {
        this.serverKey = serverKey;
        this.rootPath = rootPath;
    }

    @Override
    public String getServerKey() {
        return serverKey;
    }

    public Path getRootPath() {
        return rootPath;
    }

    @Override
    public void close() throws IOException {
        // no-op for mock gateway
    }
}
