package com.tool.otsutil.serverconnection.websocket;

import com.tool.otsutil.service.auth.AuthService;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.servlet.http.HttpSession;
import java.util.Map;

@Component
public class LoginHandshakeInterceptor implements HandshakeInterceptor {

    private final AuthService authService;

    public LoginHandshakeInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest)) {
            return false;
        }

        HttpSession session = ((ServletServerHttpRequest) request).getServletRequest().getSession(false);
        if (!authService.isLoggedIn(session)) {
            return false;
        }

        attributes.put(AuthService.LOGIN_USER_SESSION_KEY, authService.getCurrentUser(session).getUsername());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }
}
