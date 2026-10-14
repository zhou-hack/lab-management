package com.labmanage.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 注解鉴权配置
 * <p>
 * 注册拦截器后 @SaCheckLogin / @SaCheckPermission 才会生效；
 * 未注册时注解不拦截，接口等同裸奔，属于必须项。
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 全路径拦截；放行项由注解（@SaIgnore）或白名单（Knife4j 等）处理
        registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**");
    }
}
