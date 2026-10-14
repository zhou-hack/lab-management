package com.labmanage.module.space.vo;

/**
 * 楼栋视图对象（含校区信息）
 */
public record BuildingVO(
        String id,
        String campusId,
        String campusCode,
        String campusName,
        String buildingCode,
        String buildingName,
        Integer enabled
) {
}
