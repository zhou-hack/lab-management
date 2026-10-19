package com.labmanage.module.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * 物料申领行 DTO
 */
@Schema(description = "物料申领行")
public record MaterialRequestLineDTO(
        @Schema(description = "物料 ID", example = "1234567890123456789")
        @NotBlank(message = "物料不能为空")
        @Pattern(regexp = "^\\d{1,19}$", message = "物料 ID 格式无效")
        String skuId,

        @Schema(description = "申请量（大于 0）", example = "10")
        @NotNull(message = "申请量不能为空")
        @DecimalMin(value = "0", inclusive = false, message = "申请量必须大于 0")
        @Digits(integer = 15, fraction = 3, message = "申请量整数最多 15 位、小数最多 3 位")
        BigDecimal requestedQty,

        @Schema(description = "源申领行 ID（PROXY 学生代理代领必填：老师已审核源单的行）",
                example = "1234567890123456789")
        @Pattern(regexp = "^\\d{1,19}$", message = "源申领行 ID 格式无效")
        String sourceLineId
) {
}
