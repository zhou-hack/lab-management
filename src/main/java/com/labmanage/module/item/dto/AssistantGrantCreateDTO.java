package com.labmanage.module.item.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record AssistantGrantCreateDTO(
        @NotBlank(message = "学生ID不能为空") @Pattern(regexp = "^[1-9]\\d*$", message = "学生ID不合法") String studentId,
        @NotNull(message = "授权开始时间不能为空") OffsetDateTime startAt,
        @NotNull(message = "授权结束时间不能为空") OffsetDateTime endAt,
        @Size(max = 512, message = "授权原因长度不能超过512") String reason
) {
}
