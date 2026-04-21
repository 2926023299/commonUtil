package com.tool.otsutil.serverconnection.gateway;

import com.tool.otsutil.serverconnection.util.PosixPathUtils;

import java.io.IOException;

public class MockServerShell implements ServerShell {

    private volatile String cwd;
    private ServerShellListener listener;

    public MockServerShell(String initialPath) {
        this.cwd = initialPath;
    }

    @Override
    public void start(ServerShellListener listener) {
        this.listener = listener;
        listener.onStatus("connected", "mock shell connected");
        listener.onCurrentDirectory(cwd);
        listener.onOutput("Mock shell ready at " + cwd + "\n");
    }

    @Override
    public void write(String data) throws IOException {
        if (listener == null || data == null) {
            return;
        }

        String[] commands = data.split("\\r?\\n");
        for (String command : commands) {
            String trimmed = command.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (trimmed.startsWith("cd ")) {
                cwd = PosixPathUtils.normalize(cwd, trimmed.substring(3).trim());
                listener.onCurrentDirectory(cwd);
                listener.onOutput("changed directory to " + cwd + "\n");
                continue;
            }

            if ("pwd".equals(trimmed)) {
                listener.onOutput(cwd + "\n");
                listener.onCurrentDirectory(cwd);
                continue;
            }

            listener.onOutput("mock@session:" + cwd + "$ " + trimmed + "\n");
        }
    }

    @Override
    public void resize(int cols, int rows) throws IOException {
        // no-op for mock shell
    }

    @Override
    public void close() throws IOException {
        if (listener != null) {
            listener.onStatus("closed", "mock shell closed");
        }
    }
}
