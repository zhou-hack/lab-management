package com.labmanage.module.item.vo;

import java.time.Instant;
import java.util.List;

public record MaterialPlanVO(
        String id,
        String ownerTeacherId,
        String planName,
        Integer enabled,
        String remark,
        List<MaterialPlanLineVO> lines,
        Instant createTime,
        Instant updateTime
) {
}
