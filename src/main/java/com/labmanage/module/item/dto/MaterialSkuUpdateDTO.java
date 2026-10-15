package com.labmanage.module.item.dto;

import com.labmanage.module.item.enums.MaterialType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 物料 SKU 修改 DTO
 */
@Schema(description = "物料修改请求")
public record MaterialSkuUpdateDTO(
        @Schema(description = "物料 ID", example = "1234567890123456789")
        @NotBlank(message = "物料 ID 不能为空")
        @Pattern(regexp = "^\\d{1,19}$", message = "物料 ID 格式无效")
        String id,

        @Schema(description = "所属分类 ID", example = "1234567890123456789")
        @NotBlank(message = "所属分类不能为空")
        @Pattern(regexp = "^\\d{1,19}$", message = "分类 ID 格式无效")
        String categoryId,

        @Schema(description = "SKU 码（唯一）", example = "SKU-ETHANOL-500ML")
        @NotBlank(message = "SKU 码不能为空")
        @Size(max = 64, message = "SKU 码不能超过 64 个字符")
        String skuCode,

        @Schema(description = "物料名称", example = "无水乙醇 500mL")
        @NotBlank(message = "物料名称不能为空")
        @Size(max = 128, message = "物料名称不能超过 128 个字符")
        String materialName,

        @Schema(description = "规格型号", example = "AR，500mL/瓶")
        @Size(max = 500, message = "规格型号不能超过 500 个字符")
        String specification,

        @Schema(description = "基础计量单位", example = "瓶")
        @NotBlank(message = "计量单位不能为空")
        @Size(max = 16, message = "计量单位不能超过 16 个字符")
        String baseUnit,

        @Schema(description = "数量精度 0-3（整数单位填 0）", example = "0")
        @NotNull(message = "数量精度不能为空")
        @Min(value = 0, message = "数量精度最小为 0")
        @Max(value = 3, message = "数量精度最大为 3")
        Integer quantityScale,

        @Schema(description = "物料类型：CONSUMABLE 耗材 / RETURNABLE 可回收", example = "CONSUMABLE")
        @NotNull(message = "物料类型不能为空")
        MaterialType materialType,

        @Schema(description = "危险品", example = "false")
        @NotNull(message = "危险品标记不能为空")
        Boolean isHazardous,

        @Schema(description = "高价值", example = "false")
        @NotNull(message = "高价值标记不能为空")
        Boolean isValuable
) {
}
