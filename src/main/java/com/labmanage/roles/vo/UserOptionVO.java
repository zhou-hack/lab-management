package com.labmanage.roles.vo;

/**
 * 用户下拉选项（预约限制等场景选择被限制用户）
 */
public record UserOptionVO(
        String userId,
        String realName,
        String accountName,
        String personNo,
        String userType
) {
}
