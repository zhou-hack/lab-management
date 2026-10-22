package com.labmanage.module.space.vo;

import com.labmanage.module.space.enums.SpaceStatus;
import com.labmanage.module.space.enums.SpaceType;

import java.time.Instant;

/**
 * 场地详情视图。
 * id 为雪花 ID，以字符串承载避免 JavaScript 精度损失；
 * openStart/openEnd 为上海时间，HH:mm 字符串。
 */
public record SpaceDetailVO(
        String id,
        String labName,
        SpaceType labType,
        String labTypeName,
        String locationDetail,
        Integer capacity,
        String facilities,
        String openStart,
        String openEnd,
        SpaceStatus status,
        String statusName,
        Instant createTime,
        Instant updateTime
) {
}
