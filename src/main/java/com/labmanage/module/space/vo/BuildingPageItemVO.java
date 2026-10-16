package com.labmanage.module.space.vo;

import java.time.Instant;

/**
 * 楼栋分页条目（ID 序列化为字符串）
 */
public record BuildingPageItemVO(
        String id,
        String campusId,
        String campusName,
        String buildingCode,
        String buildingName,
        Integer enabled,
        Long labCount,
        Instant createTime
) {
}
