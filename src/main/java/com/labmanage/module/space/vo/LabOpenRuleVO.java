package com.labmanage.module.space.vo;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 实验室开放规则条目
 */
public record LabOpenRuleVO(
        String id,
        String labId,
        Integer weekday,
        LocalTime openTime,
        LocalTime closeTime,
        LocalDate validFrom,
        LocalDate validTo,
        Integer enabled
) {
}
