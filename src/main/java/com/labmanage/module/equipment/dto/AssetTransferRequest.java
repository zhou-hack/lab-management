package com.labmanage.module.equipment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 设备迁移登记请求（仅允许空闲且无冲突业务的资产迁移）
 */
public record AssetTransferRequest(
        @NotNull(message = "目标实验室不能为空")
        Long toLabId,

        @Size(max = 255, message = "目标存放位置长度不能超过 255")
        String toLocation,

        @Size(max = 500, message = "迁移原因长度不能超过 500")
        String reason
) {
}
