package com.labmanage.module.item.vo;

import java.util.List;

public record ClaimPreviewVO(
        boolean valid,
        List<ClaimPreviewLineVO> lines,
        List<ClaimPlannedAllocationVO> allocations,
        List<ClaimShortageVO> shortages,
        List<String> warnings,
        BookingTimeSnapshotVO bookingSnapshot
) {
}
