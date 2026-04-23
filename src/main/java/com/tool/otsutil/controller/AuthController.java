package com.tool.otsutil.controller;

import com.tool.otsutil.model.common.AppHttpCodeEnum;
import com.tool.otsutil.model.common.ResponseResult;
import com.tool.otsutil.model.dto.auth.LoginRequest;
import com.tool.otsutil.model.vo.auth.LoginUserView;
import com.tool.otsutil.service.auth.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseResult<LoginUserView> login(@RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return ResponseResult.okResult(authService.login(request, servletRequest.getSession(true)));
    }

    @PostMapping("/logout")
    public ResponseResult<Void> logout(HttpServletRequest servletRequest) {
        authService.logout(servletRequest.getSession(false));
        return ResponseResult.okResult(null);
    }

    @GetMapping("/session")
    public ResponseResult<LoginUserView> session(HttpServletRequest servletRequest) {
        HttpSession session = servletRequest.getSession(false);
        LoginUserView currentUser = authService.getCurrentUser(session);
        if (currentUser == null) {
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }
        return ResponseResult.okResult(currentUser);
    }
}
