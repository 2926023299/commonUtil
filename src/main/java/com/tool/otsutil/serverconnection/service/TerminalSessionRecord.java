package com.tool.otsutil.serverconnection.service;

import com.tool.otsutil.serverconnection.gateway.ServerConnectionHandle;
import com.tool.otsutil.serverconnection.gateway.ServerShell;
import com.tool.otsutil.serverconnection.model.view.TerminalServerMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

class TerminalSessionRecord {

    private final String sessionId = UUID.randomUUID().toString();
    private final String serverKey;
    private final String displayName;
    private final String username;
    private final ServerConnectionHandle handle;
    private final ServerShell shell;
    private final Queue<TerminalServerMessage> bufferedMessages = new ConcurrentLinkedQueue<TerminalServerMessage>();
    private volatile String cwd;
    private volatile long lastAccessAt;
    private volatile WebSocketSession webSocketSession;

    TerminalSessionRecord(String serverKey,
                          String displayName,
                          String username,
                          ServerConnectionHandle handle,
                          ServerShell shell,
                          String cwd) {
        this.serverKey = serverKey;
        this.displayName = displayName;
        this.username = username;
        this.handle = handle;
        this.shell = shell;
        this.cwd = cwd;
        touch();
    }

    String getSessionId() {
        return sessionId;
    }

    String getServerKey() {
        return serverKey;
    }

    String getDisplayName() {
        return displayName;
    }

    String getUsername() {
        return username;
    }

    ServerConnectionHandle getHandle() {
        return handle;
    }

    ServerShell getShell() {
        return shell;
    }

    String getCwd() {
        return cwd;
    }

    void setCwd(String cwd) {
        this.cwd = cwd;
    }

    long getLastAccessAt() {
        return lastAccessAt;
    }

    void touch() {
        this.lastAccessAt = System.currentTimeMillis();
    }

    WebSocketSession getWebSocketSession() {
        return webSocketSession;
    }

    void setWebSocketSession(WebSocketSession webSocketSession) {
        this.webSocketSession = webSocketSession;
    }

    Queue<TerminalServerMessage> getBufferedMessages() {
        return bufferedMessages;
    }
}
