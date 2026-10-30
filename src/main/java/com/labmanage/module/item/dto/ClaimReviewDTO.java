package com.labmanage.module.item.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ClaimReviewDTO(
        @NotNull(message = "版本号不能为空") @Min(value = 1, message = "版本号不合法") Integer revisionNo
) {
}
