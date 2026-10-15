package com.labmanage.common.serializer;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Instant 公共序列化器契约测试（UTC 三位毫秒输出 / 偏移量解析 / 无偏移量拒绝）
 */
class InstantSerializerTest {

    private final JsonMapper mapper = JsonMapper.builder()
            .addModule(new SimpleModule("instantModule")
                    .addSerializer(Instant.class, new InstantSerializer())
                    .addDeserializer(Instant.class, new InstantDeserializer()))
            .build();

    @Test
    void serializesAsUtcMillisPattern() {
        Instant instant = Instant.parse("2026-10-13T01:00:00.123456Z");
        assertEquals("\"2026-10-13T01:00:00.123Z\"", mapper.writeValueAsString(instant));
    }

    @Test
    void serializesZeroFractionAsThreeDigits() {
        assertEquals("\"2026-10-13T01:00:00.000Z\"", mapper.writeValueAsString(Instant.parse("2026-10-13T01:00:00Z")));
    }

    @Test
    void parsesZSuffixWithMillis() {
        assertEquals(Instant.parse("2026-10-13T01:00:00.000Z"),
                mapper.readValue("\"2026-10-13T01:00:00.000Z\"", Instant.class));
    }

    @Test
    void parsesNumericOffsetAndConvertsToUtcInstant() {
        // +08:00 偏移等价于 UTC 减八小时
        assertEquals(Instant.parse("2026-10-13T01:00:00Z"),
                mapper.readValue("\"2026-10-13T09:00:00+08:00\"", Instant.class));
    }

    @Test
    void rejectsTextWithoutOffset() {
        // 仓库约定：无偏移量的时间字符串必须返回 HTTP 400 / code=40000
        assertThrows(DatabindException.class,
                () -> mapper.readValue("\"2026-10-13T01:00:00\"", Instant.class));
    }

    @Test
    void rejectsBlankTextAsNull() {
        // 空字符串按 null 处理（与旧版 Jackson 2 行为一致）
        assertEquals(null, mapper.readValue("\"\"", Instant.class));
    }
}
