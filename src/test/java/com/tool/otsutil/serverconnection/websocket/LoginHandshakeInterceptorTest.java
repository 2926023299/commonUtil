package com.tool.otsutil.serverconnection.websocket;

import com.tool.otsutil.service.auth.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginHandshakeInterceptorTest {

    @Test
    void shouldRejectHandshakeWithoutLoginUser() throws Exception {
        LoginHandshakeInterceptor interceptor = new LoginHandshakeInterceptor(new AuthService());
        MockHttpServletRequest request = new MockHttpServletRequest();

        boolean allowed = interceptor.beforeHandshake(
                new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()),
                new NoOpWebSocketHandler(),
                new HashMap<String, Object>()
        );

        assertFalse(allowed);
    }

    @Test
    void shouldAllowHandshakeWithLoginUser() throws Exception {
        LoginHandshakeInterceptor interceptor = new LoginHandshakeInterceptor(new AuthService());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthService.LOGIN_USER_SESSION_KEY, "admin");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        Map<String, Object> attributes = new HashMap<String, Object>();

        boolean allowed = interceptor.beforeHandshake(
                new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()),
                new NoOpWebSocketHandler(),
                attributes
        );

        assertTrue(allowed);
        assertTrue(attributes.containsKey(AuthService.LOGIN_USER_SESSION_KEY));
    }

    private static class NoOpWebSocketHandler implements WebSocketHandler {

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
        }

        @Override
        public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) {
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus closeStatus) {
        }

        @Override
        public boolean supportsPartialMessages() {
            return false;
        }
    }
}
