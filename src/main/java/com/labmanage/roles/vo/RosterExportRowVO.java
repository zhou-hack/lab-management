package com.labmanage.roles.vo;

import java.time.Instant;

/**
 * 名单导出行（CSV 数据源，join 部门编码与名称）
 */
public record RosterExportRowVO(
        String personNo,
        String personType,
        String realName,
        String phone,
        String departmentCode,
        String departmentName,
        Integer enabled,
        Instant createTime
) {
}
