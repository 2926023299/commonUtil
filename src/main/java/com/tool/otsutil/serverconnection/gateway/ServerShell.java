package com.tool.otsutil.serverconnection.gateway;

import java.io.Closeable;
import java.io.IOException;

public interface ServerShell extends Closeable {

    void start(ServerShellListener listener) throws IOException;

    void write(String data) throws IOException;

    void resize(int cols, int rows) throws IOException;

    @Override
    void close() throws IOException;
}
