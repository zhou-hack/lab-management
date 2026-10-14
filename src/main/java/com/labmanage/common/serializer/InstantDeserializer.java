package com.labmanage.common.serializer;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * 解析带偏移量的 ISO 8601 / RFC 3339 时间为 Instant
 * 支持：2026-10-13T01:00:00Z、2026-10-13T01:00:00+08:00、2026-10-13T01:00:00.000Z
 * 无偏移量、格式无效或小数秒超过三位一律拒绝（由全局异常处理返回 40000）。
 */
public class InstantDeserializer extends ValueDeserializer<Instant> {

    private static final Pattern OVER_PRECISION = Pattern.compile("\\.\\d{4,}");

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) {
        String text = p.getText();
        if (text == null || text.isBlank()) {
            return null;
        }
        if (OVER_PRECISION.matcher(text).find()) {
            return ctxt.reportInputMismatch(Instant.class, "时间小数秒不能超过三位: %s", text);
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException e) {
            return ctxt.reportInputMismatch(Instant.class, "时间格式无效，必须为带偏移量的 ISO 8601 / RFC 3339 格式: %s", text);
        }
    }
}
