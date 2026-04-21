package com.tool.otsutil.serverconnection.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tool.otsutil.exception.CustomException;
import com.tool.otsutil.serverconnection.model.view.TerminalClientMessage;
import com.tool.otsutil.serverconnection.model.view.TerminalServerMessage;
import com.tool.otsutil.serverconnection.service.TerminalSessionManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

@Component
public class ServerTerminalWebSocketHandler extends TextWebSocketHandler {

    private final TerminalSessionManager terminalSessionManager;
    private final ObjectMapper objectMapper;

    public ServerTerminalWebSocketHandler(TerminalSessionManager terminalSessionManager, ObjectMapper objectMapper) {
        this.terminalSessionManager = terminalSessionManager;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = resolveSessionId(session);
        terminalSessionManager.attachWebSocket(sessionId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = resolveSessionId(session);
        TerminalClientMessage clientMessage = objectMapper.readValue(message.getPayload(), TerminalClientMessage.class);
        try {
            terminalSessionManager.handleTerminalMessage(sessionId, clientMessage);
        } catch (CustomException exception) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                    TerminalServerMessage.status("error", exception.getMessage())
            )));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        terminalSessionManager.detachWebSocket(resolveSessionId(session), session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(
                    TerminalServerMessage.status("error", exception.getMessage())
            )));
        }
    }

    private String resolveSessionId(WebSocketSession session) {
        String path = session.getUri() == null ? "" : session.getUri().getPath();
        int separatorIndex = path.lastIndexOf('/');
        return separatorIndex >= 0 ? path.substring(separatorIndex + 1) : path;
    }
}
