package com.labmanage.module.item.dto;

import jakarta.validation.constraints.DecimalMin;
import com.labmanage.module.item.validation.IntegerQuantity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record StockInboundLineDTO(
        @NotBlank(message = "物料不能为空") @Pattern(regexp = "^[1-9]\\d*$", message = "物料ID不合法") String materialId,
        @NotBlank(message = "库位不能为空") @Pattern(regexp = "^[1-9]\\d*$", message = "库位ID不合法") String locationId,
        @Size(max = 256, message = "库内位置长度不能超过256") String positionText,
        @NotNull(message = "入库数量不能为空") @DecimalMin(value = "1", message = "入库数量必须大于0")
        @IntegerQuantity(message = "入库数量必须为整数，整数位数不能超过15位") BigDecimal quantity
) {
}
