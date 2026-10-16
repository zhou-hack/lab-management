package com.labmanage.module.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 调拨单登记 DTO（双边库位，同一行过账产生两条流水）
 */
@Schema(description = "调拨单登记请求")
public record StockTransferCreateDTO(
        @Schema(description = "业务发生时间（带偏移量 ISO 8601，如 2026-10-14T09:00:00+08:00）", example = "2026-10-14T09:00:00+08:00")
        @NotNull(message = "业务发生时间不能为空")
        OffsetDateTime occurredAt,

        @Schema(description = "归属老师 ID（登录模块接入后由会话写入，本阶段可空）", example = "1234567890123456789")
        String ownerTeacherId,

        @Schema(description = "事由", example = "实验室搬迁调拨")
        @Size(max = 2000, message = "事由不能超过 2000 个字符")
        String reason,

        @Schema(description = "调拨明细行")
        @NotEmpty(message = "调拨明细行不能为空")
        List<@Valid StockTransferLineDTO> lines
) {
}
