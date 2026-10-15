package com.labmanage.module.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 库位新增 DTO
 */
@Schema(description = "库位新增请求")
public record StockLocationCreateDTO(
        @Schema(description = "所属实验室 ID（空间模块建成前可空）", example = "1234567890123456789")
        @Pattern(regexp = "^\\d{1,19}$", message = "实验室 ID 格式无效")
        String labId,

        @Schema(description = "库位码（同实验室内唯一）", example = "LAB1-A-01")
        @NotBlank(message = "库位码不能为空")
        @Size(max = 64, message = "库位码不能超过 64 个字符")
        String locationCode,

        @Schema(description = "库位名称", example = "一号实验室 A 区药品柜")
        @NotBlank(message = "库位名称不能为空")
        @Size(max = 128, message = "库位名称不能超过 128 个字符")
        String locationName,

        @Schema(description = "位置详述", example = "A 区 3 层左柜")
        @Size(max = 255, message = "位置详述不能超过 255 个字符")
        String locationDetail
) {
}
