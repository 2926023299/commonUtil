package com.tool.otsutil.service.auth;

import com.tool.otsutil.exception.CustomException;
import com.tool.otsutil.model.common.AppHttpCodeEnum;
import com.tool.otsutil.model.dto.auth.LoginRequest;
import com.tool.otsutil.model.vo.auth.LoginUserView;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

@Service
public class AuthService {

    public static final String LOGIN_USER_SESSION_KEY = "LOGIN_USER";

    private static final String FIXED_USERNAME = "admin";
    private static final String FIXED_PASSWORD = "JCDZ@is.01";

    public LoginUserView login(LoginRequest request, HttpSession session) {
        String username = request == null ? null : normalize(request.getUsername());
        String password = request == null ? null : request.getPassword();

        if (!FIXED_USERNAME.equals(username) || !FIXED_PASSWORD.equals(password)) {
            throw new CustomException(AppHttpCodeEnum.LOGIN_PASSWORD_ERROR);
        }

        session.setAttribute(LOGIN_USER_SESSION_KEY, FIXED_USERNAME);
        return buildUserView(FIXED_USERNAME);
    }

    public void logout(HttpSession session) {
        if (session == null) {
            return;
        }

        try {
            session.invalidate();
        } catch (IllegalStateException ignored) {
            // Ignore invalidated sessions to keep logout idempotent.
        }
    }

    public LoginUserView getCurrentUser(HttpSession session) {
        String username = getLoginUsername(session);
        return username == null ? null : buildUserView(username);
    }

    public boolean isLoggedIn(HttpSession session) {
        return getLoginUsername(session) != null;
    }

    private String getLoginUsername(HttpSession session) {
        if (session == null) {
            return null;
        }

        try {
            Object username = session.getAttribute(LOGIN_USER_SESSION_KEY);
            return username == null ? null : String.valueOf(username);
        } catch (IllegalStateException ignored) {
            return null;
        }
    }

    private LoginUserView buildUserView(String username) {
        LoginUserView view = new LoginUserView();
        view.setUsername(username);
        return view;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
