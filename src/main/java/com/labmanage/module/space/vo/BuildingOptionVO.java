package com.labmanage.module.space.vo;

/**
 * 楼栋下拉选项（实验室表单用）
 */
public record BuildingOptionVO(
        String id,
        String campusId,
        String buildingName
) {
}
