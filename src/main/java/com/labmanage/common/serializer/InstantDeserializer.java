package com.labmanage.common.serializer;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * 解析带偏移量的 ISO 8601 / RFC 3339 时间为 Instant
 * 支持：2026-10-13T01:00:00Z、2026-10-13T01:00:00+08:00、2026-10-13T01:00:00.000Z
 * <p>
 * Spring Boot 4 使用 Jackson 3（tools.jackson），基类为 ValueDeserializer；
 * 格式无效抛 DatabindException，由 Spring 转 HttpMessageNotReadableException → HTTP 400
 */
public class InstantDeserializer extends ValueDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) {
        String text = p.getText();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException e) {
            tools.jackson.databind.DatabindException failure =
                    ctxt.reportInputMismatch(this, "时间格式无效，必须为带偏移量的 ISO 8601 / RFC 3339 格式");
            throw failure;
        }
    }
}
