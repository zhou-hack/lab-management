package com.labmanage.module.item.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ClaimRejectDTO(
        @NotNull(message = "版本号不能为空") @Min(value = 1, message = "版本号不合法") Integer revisionNo,
        @NotBlank(message = "驳回原因不能为空") @Size(max = 512, message = "驳回原因长度不能超过512") String reason
) {
}
