package com.labmanage.module.space.vo;

import java.time.Instant;

/**
 * 校区分页条目（ID 序列化为字符串）
 */
public record CampusPageItemVO(
        String id,
        String campusCode,
        String campusName,
        String address,
        Integer enabled,
        Long buildingCount,
        Instant createTime
) {
}
