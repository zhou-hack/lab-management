package com.labmanage.module.space.vo;

import com.labmanage.module.space.enums.SpaceStatus;
import com.labmanage.module.space.enums.SpaceType;

import java.time.Instant;

/**
 * 场地列表行视图。
 * id 为雪花 ID，以字符串承载避免 JavaScript 精度损失；
 * openStart/openEnd 为上海本地时间，HH:mm 字符串。
 */
public record SpaceListVO(
        String id,
        String labName,
        SpaceType labType,
        String labTypeName,
        String locationDetail,
        Integer capacity,
        SpaceStatus status,
        String statusName,
        String openStart,
        String openEnd,
        Instant updateTime
) {
}
