package com.labmanage.module.item.dto;

import jakarta.validation.constraints.Size;

public record ClaimReasonDTO(
        @Size(max = 512, message = "原因长度不能超过512") String reason
) {
}
