package com.labmanage.roles.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * 助理授权创建入参。
 * <p>
 * startAt/endAt 必须为带偏移量的 ISO 8601 / RFC 3339（公共 InstantDeserializer
 * 解析并归一化到毫秒）；有效期最长一学年，半开区间 [startAt, endAt)。
 * 一期范围仅支持 LAB（场地）；OFFERING 课程范围待课程模块落地后开放。
 */
public record GrantCreateDTO(

        @NotNull(message = "被授权学生不能为空")
        Long studentId,

        @NotNull(message = "生效时间不能为空")
        Instant startAt,

        @NotNull(message = "失效时间不能为空")
        Instant endAt,

        @Size(max = 500, message = "授权说明长度不能超过 500")
        String reason,

        @NotEmpty(message = "授权范围不能为空")
        @Valid
        List<GrantScopeDTO> scopes
) {

    /**
     * 授权范围项：scopeType 一期仅 LAB；actionCode 为 ISSUE（代办领用）/ RETURN（代办归还）。
     */
    public record GrantScopeDTO(

            @NotBlank(message = "范围类型不能为空")
            @Pattern(regexp = "LAB", message = "一期仅支持 LAB 场地范围")
            String scopeType,

            @NotNull(message = "范围场地不能为空")
            Long labId,

            @NotBlank(message = "代办动作不能为空")
            @Pattern(regexp = "ISSUE|RETURN", message = "代办动作仅支持 ISSUE/RETURN")
            String actionCode
    ) {
    }
}
