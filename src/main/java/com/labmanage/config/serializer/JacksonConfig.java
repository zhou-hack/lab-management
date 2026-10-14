package com.labmanage.config.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.labmanage.common.serializer.InstantDeserializer;
import com.labmanage.common.serializer.InstantSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;

/**
 * Jackson 全局配置。
 * <p>
 * 雪花 ID 不做全局 Long→String（避免分页 total 变字符串影响前端数值组件），
 * 由各 VO 显式用 String 承载 ID，实体不对外暴露；total 等计数值不会超过 JS 安全整数。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(Instant.class, new InstantSerializer());
        module.addDeserializer(Instant.class, new InstantDeserializer());
        objectMapper.registerModule(module);
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }
}
