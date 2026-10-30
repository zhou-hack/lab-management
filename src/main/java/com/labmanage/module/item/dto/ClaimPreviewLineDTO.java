package com.labmanage.module.item.dto;

import jakarta.validation.constraints.DecimalMin;
import com.labmanage.module.item.validation.IntegerQuantity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record ClaimPreviewLineDTO(
        @NotBlank(message = "物料不能为空") @Pattern(regexp = "^[1-9]\\d*$", message = "物料ID不合法") String materialId,
        @NotNull(message = "申请数量不能为空") @DecimalMin(value = "1", message = "申请数量必须大于0")
        @IntegerQuantity(message = "申请数量必须为整数，整数位数不能超过15位") BigDecimal requestedQuantity
) {
}
