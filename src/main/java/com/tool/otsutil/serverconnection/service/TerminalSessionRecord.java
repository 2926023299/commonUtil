package com.tool.otsutil.serverconnection.service;

import com.tool.otsutil.serverconnection.gateway.ServerConnectionHandle;
import com.tool.otsutil.serverconnection.gateway.ServerShell;
import com.tool.otsutil.serverconnection.model.view.TerminalServerMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

class TerminalSessionRecord {

    static final int MAX_BUFFERED_MESSAGES = 2000;

    private final String sessionId;
    private final String serverKey;
    private final String displayName;
    private final String username;
    private final ServerConnectionHandle handle;
    private final ServerShell shell;
    private final Queue<TerminalServerMessage> bufferedMessages = new ConcurrentLinkedQueue<TerminalServerMessage>();
    private volatile String cwd;
    private volatile long lastAccessAt;
    private volatile WebSocketSession webSocketSession;
    private final String charset;

    TerminalSessionRecord(String serverKey,
                          String displayName,
                          String username,
                          ServerConnectionHandle handle,
                          ServerShell shell,
                          String cwd) {
        this(UUID.randomUUID().toString(), serverKey, displayName, username, handle, shell, cwd, "UTF-8");
    }

    TerminalSessionRecord(String serverKey,
                          String displayName,
                          String username,
                          ServerConnectionHandle handle,
                          ServerShell shell,
                          String cwd,
                          String charset) {
        this(UUID.randomUUID().toString(), serverKey, displayName, username, handle, shell, cwd, charset);
    }

    TerminalSessionRecord(String sessionId,
                          String serverKey,
                          String displayName,
                          String username,
                          ServerConnectionHandle handle,
                          ServerShell shell,
                          String cwd) {
        this(sessionId, serverKey, displayName, username, handle, shell, cwd, "UTF-8");
    }

    TerminalSessionRecord(String sessionId,
                          String serverKey,
                          String displayName,
                          String username,
                          ServerConnectionHandle handle,
                          ServerShell shell,
                          String cwd,
                          String charset) {
        this.sessionId = sessionId;
        this.serverKey = serverKey;
        this.displayName = displayName;
        this.username = username;
        this.handle = handle;
        this.shell = shell;
        this.cwd = cwd;
        this.charset = charset != null ? charset : "UTF-8";
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

    String getCharset() {
        return charset;
    }

    Queue<TerminalServerMessage> getBufferedMessages() {
        return bufferedMessages;
    }
}
