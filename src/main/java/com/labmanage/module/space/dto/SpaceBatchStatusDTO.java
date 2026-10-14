package com.labmanage.module.space.dto;

import com.labmanage.module.space.enums.SpaceStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 场地批量启停入参。
 * target 仅允许 AVAILABLE（批量启用）或 CLOSED（批量停用），
 * MAINTAINING 为维护流程专用状态，不经批量接口设置。
 */
public record SpaceBatchStatusDTO(

        @NotEmpty(message = "场地 ID 列表不能为空")
        @Size(max = 100, message = "单次批量操作最多 100 个场地")
        List<@NotNull(message = "场地 ID 不能为空") Long> ids,

        @NotNull(message = "目标状态不能为空")
        SpaceStatus target
) {
}
