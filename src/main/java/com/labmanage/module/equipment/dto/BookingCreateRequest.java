package com.labmanage.module.equipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * 器材借用单提交请求
 * <p>
 * 时间点必须使用带 Z 或数字偏移量的 ISO 8601 / RFC 3339 格式，
 * 服务端转 Instant 后按 UTC 存取；
 * 学生单 approvalTeacherId 必填（服务端按申请人身份校验）；
 * idempotencyKey 缺省时服务端生成 UUID，重复提交返回原单。
 */
public record BookingCreateRequest(
        @NotNull(message = "借用设备不能为空")
        Long assetId,

        /** 指定审批老师（学生单必填；老师本人单忽略） */
        Long approvalTeacherId,

        @NotBlank(message = "借用用途不能为空")
        @Size(max = 500, message = "借用用途长度不能超过 500")
        String purpose,

        @NotNull(message = "借用开始时间不能为空")
        OffsetDateTime startAt,

        @NotNull(message = "借用结束时间不能为空")
        OffsetDateTime endAt,

        /** 幂等键（可选，服务端缺省生成 UUID） */
        @Size(max = 128, message = "幂等键长度不能超过 128")
        String idempotencyKey
) {
}
