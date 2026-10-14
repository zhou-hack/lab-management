package com.labmanage.module.space.vo;

/**
 * 楼栋下拉选项（登录即可用，用于实验室等表单）
 */
public record BuildingOptionVO(
        String id,
        String campusId,
        String campusName,
        String buildingCode,
        String buildingName
) {
}
