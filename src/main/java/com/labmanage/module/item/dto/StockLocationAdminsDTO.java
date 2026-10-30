package com.labmanage.module.item.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record StockLocationAdminsDTO(
        @NotNull(message = "管理员集合不能为空")
        List<@NotBlank(message = "管理员ID不能为空")
        @Pattern(regexp = "^[1-9]\\d*$", message = "管理员ID不合法") String> userIds
) {
}
