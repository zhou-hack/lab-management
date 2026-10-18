package com.labmanage.module.booking.flow.vo;

import java.time.Instant;

/**
 * 预约流程时间线条目（document_event 只读视图）。
 *
 * @param eventType   事件类型码（SUBMIT/APPROVE/REJECT/CANCEL/CHECKIN/CHECKOUT/TIMEOUT_CANCEL）
 * @param eventName   事件中文名
 * @param actorType   操作人类型：USER/SYSTEM
 * @param actorName   操作人姓名（系统事件为「系统」）
 * @param fromStatus  变更前业务状态（可空）
 * @param toStatus    变更后业务状态（可空；签到 IN_USE、清点 FINISHED 为派生态）
 * @param occurredAt  发生时间（UTC 输出）
 * @param reason      事由/备注（驳回原因、撤销原因、清点备注）
 */
public record BookingTimelineEventVO(
        String eventType,
        String eventName,
        String actorType,
        String actorName,
        String fromStatus,
        String toStatus,
        Instant occurredAt,
        String reason
) {
}
