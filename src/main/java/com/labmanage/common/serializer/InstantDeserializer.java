package com.labmanage.common.serializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * 解析带偏移量的 ISO 8601 / RFC 3339 时间为 Instant
 * 支持：2026-10-13T01:00:00Z、2026-10-13T01:00:00+08:00、2026-10-13T01:00:00.000Z
 */
public class InstantDeserializer extends JsonDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String text = p.getText();
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("时间格式无效，必须为带偏移量的 ISO 8601 / RFC 3339 格式", e);
        }
    }
}
