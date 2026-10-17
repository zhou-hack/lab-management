package com.labmanage.module.space.vo;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 实验室分页条目（ID 序列化为字符串）
 */
public record LabPageItemVO(
        String id,
        String buildingId,
        String buildingName,
        String campusName,
        String departmentId,
        String departmentName,
        String labCode,
        String labName,
        String labType,
        String floorNo,
        String roomNo,
        Integer capacity,
        BigDecimal areaSqm,
        String locationNote,
        Integer enabled,
        Instant createTime
) {
}
