package com.labmanage.module.item.dto;

import com.labmanage.module.item.enums.MovementType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 库存单据登记 DTO（本阶段支持 OPENING / PURCHASE_IN / ISSUE）
 */
@Schema(description = "库存单据登记请求")
public record StockDocumentCreateDTO(
        @Schema(description = "业务类型：OPENING 期初 / PURCHASE_IN 采购入库 / ISSUE 领用出库", example = "PURCHASE_IN")
        @NotNull(message = "业务类型不能为空")
        MovementType movementType,

        @Schema(description = "业务发生时间（带偏移量 ISO 8601，如 2026-10-13T09:00:00+08:00）", example = "2026-10-13T09:00:00+08:00")
        @NotNull(message = "业务发生时间不能为空")
        OffsetDateTime occurredAt,

        @Schema(description = "归属老师 ID（登录模块接入后由会话写入，本阶段可空）", example = "1234567890123456789")
        String ownerTeacherId,

        @Schema(description = "供应商名称（采购入库填写）", example = "国药集团试剂有限公司")
        @Size(max = 128, message = "供应商名称不能超过 128 个字符")
        String vendorName,

        @Schema(description = "事由", example = "10 月耗材补货")
        @Size(max = 2000, message = "事由不能超过 2000 个字符")
        String reason,

        @Schema(description = "单据明细行")
        @NotEmpty(message = "单据明细行不能为空")
        List<@Valid StockDocumentLineCreateDTO> lines
) {
}
