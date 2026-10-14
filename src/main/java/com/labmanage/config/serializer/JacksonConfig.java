package com.labmanage.config.serializer;

import com.labmanage.common.serializer.InstantDeserializer;
import com.labmanage.common.serializer.InstantSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.module.SimpleModule;

import java.time.Instant;

/**
 * Jackson 全局配置（Jackson 3 · Spring Boot 4 自动注册 JacksonModule Bean）
 * java.time 支持已内置 databind，无需 JavaTimeModule
 */
@Configuration
public class JacksonConfig {

    @Bean
    public SimpleModule instantSimpleModule() {
        SimpleModule module = new SimpleModule("instantModule");
        module.addSerializer(Instant.class, new InstantSerializer());
        module.addDeserializer(Instant.class, new InstantDeserializer());
        return module;
    }
}
