package com.tool.otsutil.config;

import com.tool.otsutil.exception.CustomException;
import com.tool.otsutil.model.common.AppHttpCodeEnum;
import com.tool.otsutil.service.auth.AuthService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class LoginAuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    public LoginAuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (authService.isLoggedIn(request.getSession(false))) {
            return true;
        }
        throw new CustomException(AppHttpCodeEnum.NEED_LOGIN);
    }
}
