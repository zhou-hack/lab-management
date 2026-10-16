package com.labmanage.module.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 盘点实盘录入 DTO（未提交前允许重复录入修正；差异原因随实盘保存）
 */
@Schema(description = "盘点实盘录入参数")
public record StocktakeCountDTO(
        @Schema(description = "实盘行")
        @NotNull(message = "实盘行不能为空")
        List<@Valid CountLineDTO> lines
) {

    /**
     * 盘点实盘行
     */
    @Schema(description = "盘点实盘行")
    public record CountLineDTO(
            @Schema(description = "盘点行 ID", example = "1234567890123456789")
            @NotBlank(message = "盘点行 ID 不能为空")
            @Pattern(regexp = "^\\d{1,19}$", message = "盘点行 ID 格式无效")
            String lineId,

            @Schema(description = "实盘量（非负）", example = "18")
            @NotNull(message = "实盘量不能为空")
            @DecimalMin(value = "0", message = "实盘量不能为负")
            @Digits(integer = 15, fraction = 3, message = "实盘量整数最多 15 位、小数最多 3 位")
            BigDecimal countedQty,

            @Schema(description = "差异原因（差异非零必填）", example = "一瓶破损未登记报废")
            @Size(max = 512, message = "差异原因不超过 512 个字符")
            String differenceReason
    ) {
    }
}
