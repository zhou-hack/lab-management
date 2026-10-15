package com.labmanage.module.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * 归还单明细行 DTO（绑定原出库行，可多次部分归还；累计结算量不超过原出库量）
 */
@Schema(description = "归还单明细行")
public record StockReturnLineDTO(
        @Schema(description = "原出库行 ID（stock_document_line.id，领用出库过账后产生）", example = "1234567890123456789")
        @NotBlank(message = "原出库行不能为空")
        @Pattern(regexp = "^\\d{1,19}$", message = "原出库行 ID 格式无效")
        String originalIssueLineId,

        @Schema(description = "归还入库位 ID（可空，默认回原出库位）", example = "1234567890123456789")
        @Pattern(regexp = "^\\d{1,19}$", message = "归还入库位 ID 格式无效")
        String toLocationId,

        @Schema(description = "本次归还量", example = "3")
        @NotNull(message = "归还数量不能为空")
        @DecimalMin(value = "0", inclusive = false, message = "归还数量必须大于 0")
        @Digits(integer = 15, fraction = 3, message = "归还数量整数最多 15 位、小数最多 3 位")
        BigDecimal quantity
) {
}
