package com.labmanage.roles.vo;

/**
 * 策略参数字段元数据（结构化表单 schema）
 *
 * @param type  字段类型，当前仅 INT
 * @param min   最小值（含）
 * @param max   最大值（含）
 * @param unit  展示单位（次/分钟/秒/条）
 */
public record PolicySchemaFieldVO(
        String key,
        String label,
        String type,
        Integer min,
        Integer max,
        String unit
) {
}
