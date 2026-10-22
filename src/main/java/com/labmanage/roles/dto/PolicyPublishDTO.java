package com.labmanage.roles.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 策略发布请求（发布后不可修改；版本号由服务端按同码递增）
 */
public record PolicyPublishDTO(

        @NotBlank(message = "策略编码不能为空")
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,63}$", message = "策略编码需为 2-64 位大写字母、数字或下划线，且以字母开头")
        String policyCode,

        @NotBlank(message = "策略名称不能为空")
        @Size(max = 128, message = "策略名称长度不能超过 128")
        String policyName,

        @NotNull(message = "生效开始不能为空")
        Instant effectiveFrom,

        /** 生效结束；NULL 表示长期 */
        Instant effectiveTo,

        @NotBlank(message = "策略参数不能为空")
        String config
) {
}
