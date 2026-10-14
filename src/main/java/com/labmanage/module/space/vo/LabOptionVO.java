package com.labmanage.module.space.vo;

/**
 * 实验室下拉选项（登录即可用，用于设备保管位置、预约等表单）
 */
public record LabOptionVO(
        String id,
        String labCode,
        String labName,
        String buildingId,
        String buildingName,
        String campusId,
        String campusName,
        String floorNo,
        String roomNo
) {
}
