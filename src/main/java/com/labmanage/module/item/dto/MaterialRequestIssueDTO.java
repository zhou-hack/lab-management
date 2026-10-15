package com.labmanage.module.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 申领实发出库 DTO（生成关联申领行的 ISSUE 草稿单，审批与过账走库存单据既有流程）
 */
@Schema(description = "申领实发出库参数")
public record MaterialRequestIssueDTO(
        @Schema(description = "业务发生时间（带偏移量 ISO 8601）", example = "2026-10-14T01:00:00+08:00")
        @NotNull(message = "业务发生时间不能为空")
        OffsetDateTime occurredAt,

        @Schema(description = "默认出库位 ID（未指定行的出库位）", example = "1234567890123456789")
        @NotBlank(message = "出库位不能为空")
        @Pattern(regexp = "^\\d{1,19}$", message = "出库位 ID 格式无效")
        String fromLocationId,

        @Schema(description = "事由", example = "实验课发放")
        @Size(max = 512, message = "事由不超过 512 个字符")
        String reason,

        @Schema(description = "实发行（为空时全部可发行按剩余批准量发放）")
        java.util.List<IssueLineDTO> lines
) {

    /**
     * 申领实发行
     */
    @Schema(description = "申领实发行")
    public record IssueLineDTO(
            @Schema(description = "申领行 ID", example = "1234567890123456789")
            @NotBlank(message = "申领行 ID 不能为空")
            @Pattern(regexp = "^\\d{1,19}$", message = "申领行 ID 格式无效")
            String lineId,

            @Schema(description = "本次实发量（不超过剩余批准量）", example = "5")
            @NotNull(message = "实发量不能为空")
            @DecimalMin(value = "0", inclusive = false, message = "实发量必须大于 0")
            @Digits(integer = 15, fraction = 3, message = "实发量整数最多 15 位、小数最多 3 位")
            BigDecimal quantity,

            @Schema(description = "出库位 ID（为空用默认出库位）", example = "1234567890123456789")
            @Pattern(regexp = "^\\d{1,19}$", message = "出库位 ID 格式无效")
            String fromLocationId
    ) {
    }
}
