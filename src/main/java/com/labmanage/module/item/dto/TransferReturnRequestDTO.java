package com.labmanage.module.item.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TransferReturnRequestDTO(
        @Size(max = 128, message = "幂等键长度不能超过128") String idempotencyKey,
        @NotBlank(message = "拒收原因不能为空") @Size(max = 512, message = "拒收原因长度不能超过512") String reason
) {
}
