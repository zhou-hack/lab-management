package com.labmanage.config;

import tools.jackson.databind.ObjectMapper;
import com.labmanage.common.security.JwtAuthFilter;
import com.labmanage.common.security.JwtService;
import com.labmanage.roles.service.AuthService;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 安全配置：注册 JWT 认证过滤器（/api/v1/** 默认要求 access token，
 * 公开端点见 JwtAuthFilter.PUBLIC_PATHS）；方法级鉴权用 @RequirePermission。
 */
@Configuration
public class SecurityConfig {

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilter(JwtService jwtService,
                                                               AuthService authService,
                                                               ObjectMapper objectMapper) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new JwtAuthFilter(jwtService, authService, objectMapper));
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(1);
        return registration;
    }
}
