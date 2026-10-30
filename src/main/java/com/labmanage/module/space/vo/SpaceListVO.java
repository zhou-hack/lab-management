package com.labmanage.module.space.vo;

import com.labmanage.module.space.enums.SpaceStatus;
import com.labmanage.module.space.enums.SpaceType;

import java.time.Instant;
import java.util.List;

/**
 * 场地列表行视图。
 * id 为雪花 ID，以字符串承载避免 JavaScript 精度损失；
 * openStart/openEnd 为上海本地时间，HH:mm 字符串；
 * upcomingMaintenance 为生效中（含进行中与未来）的维修时段，按开始时间升序。
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
        Instant updateTime,
        List<MaintenanceWindowVO> upcomingMaintenance
) {
}
