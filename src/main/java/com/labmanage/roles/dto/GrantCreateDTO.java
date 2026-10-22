package com.labmanage.roles.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * 创建助理授权请求（授权老师取当前登录人，客户端不得指定）
 *
 * @param capabilities 授权权力类型（当前仅支持 MATERIAL_ISSUE 物料领取；结构可扩展，一单可含多种）
 */
public record GrantCreateDTO(

        @NotNull(message = "被授权学生不能为空")
        Long studentId,

        @NotNull(message = "授权开始时间不能为空")
        Instant startAt,

        @NotNull(message = "授权结束时间不能为空")
        Instant endAt,

        @NotEmpty(message = "授权权力不能为空")
        List<@Pattern(regexp = "MATERIAL_ISSUE", message = "授权权力类型无效") String> capabilities,

        @Size(max = 500, message = "授权说明不能超过 500 字")
        String reason
) {
}
