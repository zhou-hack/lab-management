package com.labmanage.module.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 领用定额修改 DTO
 */
@Schema(description = "领用定额修改参数")
public record MaterialQuotaUpdateDTO(
        @Schema(description = "每人每课时定额（非负）", example = "0.05")
        @NotNull(message = "每人每课时定额不能为空")
        @DecimalMin(value = "0", message = "每人每课时定额不能为负")
        @Digits(integer = 15, fraction = 3, message = "定额整数最多 15 位、小数最多 3 位")
        BigDecimal qtyPerStudentPeriod
) {
}
