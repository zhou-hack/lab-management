package com.labmanage.module.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.List;

/**
 * 领用定额批量登记 DTO（开课实例 + SKU 已存在则更新量，不存在则新增并启用）
 */
@Schema(description = "领用定额批量登记参数")
public record MaterialQuotaUpsertDTO(
        @Schema(description = "开课实例 ID", example = "1234567890123456789")
        @NotBlank(message = "开课实例不能为空")
        @Pattern(regexp = "^\\d{1,19}$", message = "开课实例 ID 格式无效")
        String offeringId,

        @Schema(description = "定额行")
        @NotEmpty(message = "定额行不能为空")
        List<@Valid QuotaLineDTO> lines
) {

    /**
     * 定额行
     */
    @Schema(description = "领用定额行")
    public record QuotaLineDTO(
            @Schema(description = "物料 ID", example = "1234567890123456789")
            @NotBlank(message = "物料不能为空")
            @Pattern(regexp = "^\\d{1,19}$", message = "物料 ID 格式无效")
            String skuId,

            @Schema(description = "每人每课时定额（非负）", example = "0.05")
            @NotNull(message = "每人每课时定额不能为空")
            @DecimalMin(value = "0", message = "每人每课时定额不能为负")
            @Digits(integer = 15, fraction = 3, message = "定额整数最多 15 位、小数最多 3 位")
            BigDecimal qtyPerStudentPeriod
    ) {
    }
}
