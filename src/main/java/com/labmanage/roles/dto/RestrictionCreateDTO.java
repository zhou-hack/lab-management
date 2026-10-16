package com.labmanage.roles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 手工新增预约限制请求（source_document_id 恒为 NULL，允许多条，时段由服务端校验）
 */
public record RestrictionCreateDTO(

        @NotNull(message = "被限制用户不能为空")
        Long userId,

        @NotBlank(message = "限制类型不能为空")
        @Pattern(regexp = "^(EQUIPMENT|LAB|ALL)$", message = "限制类型仅支持 EQUIPMENT/LAB/ALL")
        String restrictionType,

        @Size(max = 500, message = "限制原因长度不能超过 500")
        String reason,

        @NotNull(message = "限制开始时间不能为空")
        Instant startAt,

        /** NULL = 直至撤销 */
        Instant endAt
) {
}
