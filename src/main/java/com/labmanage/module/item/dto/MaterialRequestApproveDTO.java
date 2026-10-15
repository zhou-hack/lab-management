package com.labmanage.module.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.List;

/**
 * 物料申领单审批 DTO（逐行写批准量；未指定的行按申请量批准）
 */
@Schema(description = "物料申领单审批参数")
public record MaterialRequestApproveDTO(
        @Schema(description = "行批准量（为空时全部行按申请量批准）")
        @Valid
        List<ApproveLineDTO> lines
) {

    /**
     * 申领行批准量
     */
    @Schema(description = "申领行批准量")
    public record ApproveLineDTO(
            @Schema(description = "申领行 ID", example = "1234567890123456789")
            @NotBlank(message = "申领行 ID 不能为空")
            @Pattern(regexp = "^\\d{1,19}$", message = "申领行 ID 格式无效")
            String lineId,

            @Schema(description = "批准量（不超过申请量）", example = "8")
            @NotNull(message = "批准量不能为空")
            @DecimalMin(value = "0", inclusive = false, message = "批准量必须大于 0")
            @Digits(integer = 15, fraction = 3, message = "批准量整数最多 15 位、小数最多 3 位")
            BigDecimal approvedQty
    ) {
    }
}
