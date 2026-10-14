package com.labmanage.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 配置：/api/v1 默认要求登录，仅放行认证公开端点；
 * 注解鉴权（@SaCheckLogin/@SaCheckPermission）由 SaInterceptor 处理。
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler -> SaRouter
                        .match("/api/v1/**")
                        .notMatch("/api/v1/auth/login", "/api/v1/auth/sms/send", "/api/v1/auth/sms/login")
                        .check(route -> StpUtil.checkLogin())))
                .addPathPatterns("/**");
    }
}
