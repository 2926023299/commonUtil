package com.tool.otsutil.serverconnection.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class ServerTerminalWebSocketConfig implements WebSocketConfigurer {

    private final ServerTerminalWebSocketHandler handler;

    public ServerTerminalWebSocketConfig(ServerTerminalWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/server-connections/terminal/**")
                .setAllowedOrigins("*");
    }
}
