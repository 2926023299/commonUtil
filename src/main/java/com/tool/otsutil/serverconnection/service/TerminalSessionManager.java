package com.tool.otsutil.serverconnection.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tool.otsutil.exception.CustomException;
import com.tool.otsutil.model.common.AppHttpCodeEnum;
import com.tool.otsutil.model.dto.inspection.ServerConfig;
import com.tool.otsutil.serverconnection.config.ServerConnectionProperties;
import com.tool.otsutil.serverconnection.gateway.DownloadedRemoteFile;
import com.tool.otsutil.serverconnection.gateway.RemoteServerGateway;
import com.tool.otsutil.serverconnection.gateway.ServerConnectionHandle;
import com.tool.otsutil.serverconnection.gateway.ServerShell;
import com.tool.otsutil.serverconnection.gateway.ServerShellListener;
import com.tool.otsutil.serverconnection.model.request.CreateDirectoryRequest;
import com.tool.otsutil.serverconnection.model.request.DeleteRemoteFileRequest;
import com.tool.otsutil.serverconnection.model.request.OpenTerminalSessionRequest;
import com.tool.otsutil.serverconnection.model.request.RenameRemoteFileRequest;
import com.tool.otsutil.serverconnection.model.view.RemoteFileListView;
import com.tool.otsutil.serverconnection.model.view.ServerConnectionView;
import com.tool.otsutil.serverconnection.model.view.TerminalClientMessage;
import com.tool.otsutil.serverconnection.model.view.TerminalServerMessage;
import com.tool.otsutil.serverconnection.model.view.TerminalSessionView;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TerminalSessionManager {

    private final Map<String, TerminalSessionRecord> sessions = new ConcurrentHashMap<String, TerminalSessionRecord>();
    private final ServerCatalogService serverCatalogService;
    private final RemoteServerGateway remoteServerGateway;
    private final ServerConnectionProperties properties;
    private final ObjectMapper objectMapper;

    public TerminalSessionManager(ServerCatalogService serverCatalogService,
                                  RemoteServerGateway remoteServerGateway,
                                  ServerConnectionProperties properties,
                                  ObjectMapper objectMapper) {
        this.serverCatalogService = serverCatalogService;
        this.remoteServerGateway = remoteServerGateway;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public List<ServerConnectionView> listServers() {
        return serverCatalogService.listServers();
    }

    public TerminalSessionView openSession(OpenTerminalSessionRequest request) throws IOException {
        if (request == null || request.getServerKey() == null || request.getServerKey().trim().isEmpty()) {
            throw new CustomException(AppHttpCodeEnum.PARAM_REQUIRE, "缺少服务器标识");
        }

        ServerConfig serverConfig = serverCatalogService.getServerConfig(request.getServerKey());
        ServerConnectionHandle handle = remoteServerGateway.openConnection(serverConfig);
        String cwd = remoteServerGateway.resolveHomeDirectory(handle);
        if (request.getInitialPath() != null && !request.getInitialPath().trim().isEmpty()) {
            cwd = remoteServerGateway.canonicalizePath(handle, cwd, request.getInitialPath());
        }
        ServerShell shell = remoteServerGateway.openShell(handle, cwd);

        TerminalSessionRecord record = new TerminalSessionRecord(
                request.getServerKey(),
                serverCatalogService.buildDisplayName(serverConfig),
                serverConfig.getUsername(),
                handle,
                shell,
                cwd
        );
        sessions.put(record.getSessionId(), record);

        shell.start(new ServerShellListener() {
            @Override
            public void onOutput(String data) {
                bufferOrSend(record, TerminalServerMessage.output(data));
            }

            @Override
            public void onStatus(String status, String message) {
                bufferOrSend(record, TerminalServerMessage.status(status, message));
            }

            @Override
            public void onCurrentDirectory(String cwdValue) {
                record.setCwd(cwdValue);
                bufferOrSend(record, TerminalServerMessage.cwd(cwdValue));
            }
        });

        return toView(record);
    }

    public void closeSession(String sessionId) {
        TerminalSessionRecord record = requireSession(sessionId);
        closeRecord(record, CloseStatus.NORMAL);
    }

    public TerminalSessionView getSession(String sessionId) {
        return toView(requireSession(sessionId));
    }

    public RemoteFileListView listFiles(String sessionId, String path) throws IOException {
        TerminalSessionRecord record = requireSession(sessionId);
        RemoteFileListView view = remoteServerGateway.listFiles(record.getHandle(), record.getCwd(), path);
        record.setCwd(view.getCwd());
        record.touch();
        bufferOrSend(record, TerminalServerMessage.cwd(view.getCwd()));
        return view;
    }

    public String createDirectory(String sessionId, CreateDirectoryRequest request) throws IOException {
        TerminalSessionRecord record = requireSession(sessionId);
        String currentPath = remoteServerGateway.createDirectory(record.getHandle(), record.getCwd(), request.getPath(), request.getName());
        record.touch();
        return currentPath;
    }

    public String rename(String sessionId, RenameRemoteFileRequest request) throws IOException {
        TerminalSessionRecord record = requireSession(sessionId);
        String path = remoteServerGateway.rename(record.getHandle(), record.getCwd(), request.getFromPath(), request.getToPath());
        record.touch();
        return path;
    }

    public void delete(String sessionId, DeleteRemoteFileRequest request) throws IOException {
        TerminalSessionRecord record = requireSession(sessionId);
        boolean recursive = request.getRecursive() != null && request.getRecursive();
        remoteServerGateway.delete(record.getHandle(), record.getCwd(), request.getPath(), recursive);
        record.touch();
    }

    public DownloadedRemoteFile downloadFile(String sessionId, String path) throws IOException {
        TerminalSessionRecord record = requireSession(sessionId);
        record.touch();
        return remoteServerGateway.downloadFile(record.getHandle(), record.getCwd(), path);
    }

    public void uploadFiles(String sessionId, String path, MultipartFile[] files) throws IOException {
        TerminalSessionRecord record = requireSession(sessionId);
        remoteServerGateway.uploadFiles(record.getHandle(), record.getCwd(), path, files);
        record.touch();
    }

    public void attachWebSocket(String sessionId, WebSocketSession webSocketSession) throws IOException {
        TerminalSessionRecord record = requireSession(sessionId);
        WebSocketSession current = record.getWebSocketSession();
        if (current != null && current.isOpen() && current != webSocketSession) {
            current.close(CloseStatus.NORMAL);
        }
        record.setWebSocketSession(webSocketSession);
        record.touch();

        send(record, TerminalServerMessage.status("connected", "websocket attached"));
        send(record, TerminalServerMessage.cwd(record.getCwd()));
        flushBuffered(record);
    }

    public void detachWebSocket(String sessionId, WebSocketSession webSocketSession) {
        TerminalSessionRecord record = sessions.get(sessionId);
        if (record == null) {
            return;
        }
        if (record.getWebSocketSession() == webSocketSession) {
            record.setWebSocketSession(null);
        }
    }

    public void handleTerminalMessage(String sessionId, TerminalClientMessage message) throws IOException {
        TerminalSessionRecord record = requireSession(sessionId);
        record.touch();
        if (message == null || message.getType() == null) {
            throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "终端消息类型不能为空");
        }

        if ("input".equals(message.getType())) {
            record.getShell().write(message.getData() == null ? "" : message.getData());
            return;
        }

        if ("resize".equals(message.getType())) {
            record.getShell().resize(message.getCols() == null ? 120 : message.getCols(), message.getRows() == null ? 32 : message.getRows());
            return;
        }

        if ("ping".equals(message.getType())) {
            send(record, TerminalServerMessage.status("connected", "pong"));
            send(record, TerminalServerMessage.cwd(record.getCwd()));
            return;
        }

        throw new CustomException(AppHttpCodeEnum.PARAM_INVALID, "不支持的终端消息类型");
    }

    @Scheduled(fixedDelayString = "${server-connections.cleanup-delay-ms:60000}")
    public void cleanupIdleSessions() {
        long now = System.currentTimeMillis();
        long idleThreshold = properties.getIdleTimeoutMinutes() * 60L * 1000L;
        List<TerminalSessionRecord> expired = new ArrayList<TerminalSessionRecord>();

        for (TerminalSessionRecord record : sessions.values()) {
            if (now - record.getLastAccessAt() > idleThreshold) {
                expired.add(record);
            }
        }

        for (TerminalSessionRecord record : expired) {
            closeRecord(record, CloseStatus.GOING_AWAY);
        }
    }

    @PreDestroy
    public void closeAllSessions() {
        for (TerminalSessionRecord record : new ArrayList<TerminalSessionRecord>(sessions.values())) {
            closeRecord(record, CloseStatus.GOING_AWAY);
        }
    }

    private TerminalSessionRecord requireSession(String sessionId) {
        TerminalSessionRecord record = sessions.get(sessionId);
        if (record == null) {
            throw new CustomException(AppHttpCodeEnum.DATA_NOT_EXIST, "终端会话不存在");
        }
        return record;
    }

    private TerminalSessionView toView(TerminalSessionRecord record) {
        TerminalSessionView view = new TerminalSessionView();
        view.setSessionId(record.getSessionId());
        view.setServerKey(record.getServerKey());
        view.setDisplayName(record.getDisplayName());
        view.setUsername(record.getUsername());
        view.setCwd(record.getCwd());
        return view;
    }

    private void bufferOrSend(TerminalSessionRecord record, TerminalServerMessage message) {
        try {
            if (!send(record, message)) {
                record.getBufferedMessages().add(message);
            }
        } catch (IOException exception) {
            record.getBufferedMessages().add(message);
        }
    }

    private void flushBuffered(TerminalSessionRecord record) throws IOException {
        TerminalServerMessage message;
        while ((message = record.getBufferedMessages().poll()) != null) {
            if (!send(record, message)) {
                record.getBufferedMessages().add(message);
                return;
            }
        }
    }

    private boolean send(TerminalSessionRecord record, TerminalServerMessage message) throws IOException {
        WebSocketSession webSocketSession = record.getWebSocketSession();
        if (webSocketSession == null || !webSocketSession.isOpen()) {
            return false;
        }

        synchronized (webSocketSession) {
            webSocketSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        }
        return true;
    }

    private void closeRecord(TerminalSessionRecord record, CloseStatus closeStatus) {
        sessions.remove(record.getSessionId());

        try {
            WebSocketSession webSocketSession = record.getWebSocketSession();
            if (webSocketSession != null && webSocketSession.isOpen()) {
                webSocketSession.close(closeStatus);
            }
        } catch (IOException ignored) {
        }

        try {
            record.getShell().close();
        } catch (IOException ignored) {
        }

        try {
            record.getHandle().close();
        } catch (IOException ignored) {
        }
    }
}
