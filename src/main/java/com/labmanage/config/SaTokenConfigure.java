package com.labmanage.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 注解拦截器注册。
 * <p>
 * {@code @SaCheckPermission} 等注解依赖 SaInterceptor 在 MVC 链路中生效，
 * 未注册时注解形同虚设（接口不受鉴权保护）。
 * 拦截全部路径，注解本身按需放行（配合 @SaIgnore）。
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor())
                .addPathPatterns("/**");
    }
}
