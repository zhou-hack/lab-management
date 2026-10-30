package com.labmanage.module.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * 库存单据明细行 DTO
 */
@Schema(description = "库存单据明细行")
public record StockDocumentLineCreateDTO(
        @Schema(description = "物料 ID", example = "1234567890123456789")
        @NotBlank(message = "物料不能为空")
        @Pattern(regexp = "^\\d{1,19}$", message = "物料 ID 格式无效")
        String skuId,

        @Schema(description = "出库位 ID（领用出库必填，入库类型不填）", example = "1234567890123456789")
        @Pattern(regexp = "^\\d{1,19}$", message = "出库位 ID 格式无效")
        String fromLocationId,

        @Schema(description = "入库位 ID（期初/采购入库必填，领用出库不填）", example = "1234567890123456789")
        @Pattern(regexp = "^\\d{1,19}$", message = "入库位 ID 格式无效")
        String toLocationId,

        @Schema(description = "数量（精度不超过物料数量精度）", example = "10")
        @NotNull(message = "数量不能为空")
        @DecimalMin(value = "0", inclusive = false, message = "数量必须大于 0")
        @Digits(integer = 15, fraction = 3, message = "数量整数最多 15 位、小数最多 3 位")
        BigDecimal quantity
) {
}
