package com.labmanage.module.space.vo;

import java.math.BigDecimal;

/**
 * 实验室视图对象（含楼栋/校区/部门信息）
 */
public record LabVO(
        String id,
        String buildingId,
        String buildingCode,
        String buildingName,
        String campusId,
        String campusCode,
        String campusName,
        String departmentId,
        String departmentName,
        String labCode,
        String labName,
        String labType,
        String floorNo,
        String roomNo,
        Integer capacity,
        BigDecimal areaSqm,
        String locationNote,
        Integer enabled
) {
}
