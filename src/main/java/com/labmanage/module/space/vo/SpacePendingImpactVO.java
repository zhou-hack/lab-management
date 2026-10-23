package com.labmanage.module.space.vo;

/**
 * 场地停用影响评估（联动提醒）：该场地未来未释放占用槽数与涉及预约单数，
 * 前端停用二次确认时展示，提示停用后新预约将被拦截且需跟进已有预约。
 */
public record SpacePendingImpactVO(
        long futureSlots,
        long futureBookings
) {
}
