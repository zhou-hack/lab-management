package com.labmanage.module.booking.vo;

import java.util.List;

/**
 * 批量审批结果：approvedIds 为成功单号；failedItems 含失败原因
 * （状态已变更/时段冲突/自审拦截等），整体仍返回成功由前端分档提示。
 */
public record BatchApproveResultVO(
        List<String> approvedIds,
        List<FailedItem> failedItems
) {

    /** 单条失败明细 */
    public record FailedItem(String id, String msg) {
    }
}
