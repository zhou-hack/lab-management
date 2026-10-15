package com.labmanage.module.space.vo;

import com.labmanage.module.space.enums.SpaceStatus;
import com.labmanage.module.space.enums.SpaceType;

/**
 * 场地下拉选项（供预约等下游模块与选择器联调）。
 * id 为雪花 ID，以字符串承载避免 JavaScript 精度损失。
 */
public record SpaceOptionVO(
        String id,
        String labName,
        SpaceType labType,
        Integer capacity,
        SpaceStatus status
) {
}
