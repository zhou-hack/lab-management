package com.labmanage.module.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 物料分类新增 DTO
 */
@Schema(description = "物料分类新增请求")
public record MaterialCategoryCreateDTO(
        @Schema(description = "分类编码（唯一）", example = "CAT-CHEM")
        @NotBlank(message = "分类编码不能为空")
        @Size(max = 64, message = "分类编码不能超过 64 个字符")
        String categoryCode,

        @Schema(description = "分类名称", example = "化学试剂")
        @NotBlank(message = "分类名称不能为空")
        @Size(max = 128, message = "分类名称不能超过 128 个字符")
        String categoryName,

        @Schema(description = "父分类 ID（空为根节点）", example = "1234567890123456789")
        @Pattern(regexp = "^\\d{1,19}$", message = "父分类 ID 格式无效")
        String parentId
) {
}
