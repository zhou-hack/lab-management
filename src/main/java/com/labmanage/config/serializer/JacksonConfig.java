package com.labmanage.config.serializer;

import com.labmanage.common.serializer.InstantDeserializer;
import com.labmanage.common.serializer.InstantSerializer;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.module.SimpleModule;

import java.time.Instant;

/**
 * Jackson 全局配置（Jackson 3 / tools.jackson）
 * 通过 JsonMapperBuilderCustomizer 把 Instant 统一序列化模块注册进 Web 层 JsonMapper。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer instantJacksonModuleCustomizer() {
        SimpleModule module = new SimpleModule()
                .addSerializer(Instant.class, new InstantSerializer())
                .addDeserializer(Instant.class, new InstantDeserializer());
        return builder -> builder.addModule(module);
    }
}
