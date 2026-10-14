package com.labmanage.module.space.vo;

/**
 * 校区视图对象
 */
public record CampusVO(
        String id,
        String campusCode,
        String campusName,
        String address,
        Integer enabled
) {
}
