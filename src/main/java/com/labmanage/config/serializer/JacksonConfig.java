package com.labmanage.config.serializer;

import com.labmanage.common.serializer.InstantDeserializer;
import com.labmanage.common.serializer.InstantSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;

import java.time.Instant;

/**
 * Jackson 全局配置（Jackson 3 / tools.jackson）
 * <p>
 * Spring Boot 4 的 HTTP 消息转换基于 Jackson 3，
 * 自动收集容器中所有 JacksonModule bean 并注册到共享 JsonMapper，
 * 因此不再手写 ObjectMapper，仅以模块方式贡献 Instant 统一序列化约定。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JacksonModule instantModule() {
        SimpleModule module = new SimpleModule("instantModule");
        module.addSerializer(Instant.class, new InstantSerializer());
        module.addDeserializer(Instant.class, new InstantDeserializer());
        return module;
    }
}
