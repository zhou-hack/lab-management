package com.labmanage.roles.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * 创建助理授权请求（授权老师取当前登录人，客户端不得指定）
 */
public record GrantCreateDTO(

        @NotNull(message = "被授权学生不能为空")
        Long studentId,

        @NotNull(message = "授权开始时间不能为空")
        Instant startAt,

        @NotNull(message = "授权结束时间不能为空")
        Instant endAt,

        @Size(max = 500, message = "授权说明不能超过 500 字")
        String reason
) {
}
