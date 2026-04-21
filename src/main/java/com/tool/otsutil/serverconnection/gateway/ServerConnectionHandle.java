package com.tool.otsutil.serverconnection.gateway;

import java.io.Closeable;
import java.io.IOException;

public interface ServerConnectionHandle extends Closeable {

    String getServerKey();

    @Override
    void close() throws IOException;
}
