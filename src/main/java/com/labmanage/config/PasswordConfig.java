package com.labmanage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码散列配置：BCrypt，版本由 Spring Boot 依赖管理控制。
 * B12：显式 12 轮强度（默认 10 偏弱）；存量散列自携 cost 因子，验证不受影响。
 */
@Configuration
public class PasswordConfig {

    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
