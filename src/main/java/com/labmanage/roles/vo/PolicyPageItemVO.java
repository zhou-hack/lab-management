package com.labmanage.roles.vo;

import java.time.Instant;

/**
 * 策略分页条目
 */
public record PolicyPageItemVO(
        String id,
        String policyCode,
        Integer policyVersion,
        String policyName,
        Instant effectiveFrom,
        Instant effectiveTo,
        String config,
        Integer enabled,
        Instant createTime
) {
}
