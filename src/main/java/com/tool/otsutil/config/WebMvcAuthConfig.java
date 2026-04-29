package com.tool.otsutil.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcAuthConfig implements WebMvcConfigurer {

    private final LoginAuthInterceptor loginAuthInterceptor;

    public WebMvcAuthConfig(LoginAuthInterceptor loginAuthInterceptor) {
        this.loginAuthInterceptor = loginAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginAuthInterceptor)
                .addPathPatterns("/Inspection/**", "/server-connections/**", "/mysql-workbench/**");
    }
}
