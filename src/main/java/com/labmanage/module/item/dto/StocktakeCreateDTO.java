package com.labmanage.module.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

/**
 * 盘点单创建 DTO（建单即快照库位全部账面量与余额版本）
 */
@Schema(description = "盘点单创建参数")
public record StocktakeCreateDTO(
        @Schema(description = "盘点库位 ID", example = "1234567890123456789")
        @NotBlank(message = "盘点库位不能为空")
        @Pattern(regexp = "^\\d{1,19}$", message = "库位 ID 格式无效")
        String locationId,

        @Schema(description = "学期 ID", example = "1234567890123456789")
        @Pattern(regexp = "^\\d{1,19}$", message = "学期 ID 格式无效")
        String semesterId,

        @Schema(description = "盘点日期（纯日期，不做时区转换）", example = "2026-10-14")
        @NotNull(message = "盘点日期不能为空")
        LocalDate countDate
) {
}
