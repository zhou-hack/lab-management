package com.labmanage.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 配置：/api/v1 默认要求登录，仅放行认证公开端点；
 * 注解鉴权（@SaCheckLogin/@SaCheckPermission）由 SaInterceptor 处理。
 * <p>
 * B2：API 文档端点（Swagger/Knife4j）默认要求登录且 ADMIN 角色；
 * 仅 dev 环境经 lab.docs.public=true 免登录公开，便于联调。
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /** API 文档是否免登录公开（application.yml 默认 false，application-dev.yml 置 true） */
    @Value("${lab.docs.public:false}")
    private boolean docsPublic;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handler -> {
            SaRouter.match("/api/v1/**")
                    .notMatch("/api/v1/auth/login", "/api/v1/auth/sms/send", "/api/v1/auth/sms/login")
                    .check(route -> StpUtil.checkLogin());
            if (!docsPublic) {
                SaRouter.match("/v3/api-docs", "/v3/api-docs/**", "/doc.html",
                                "/webjars/**", "/swagger-resources/**")
                        .check(route -> {
                            StpUtil.checkLogin();
                            StpUtil.checkRole("ADMIN");
                        });
            }
        }))
                .addPathPatterns("/**");
    }
}
